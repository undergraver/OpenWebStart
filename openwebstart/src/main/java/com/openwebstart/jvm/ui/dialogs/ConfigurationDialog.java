package com.openwebstart.jvm.ui.dialogs;

import com.openwebstart.config.OwsDefaultsProvider;
import com.openwebstart.controlpanel.ButtonPanelFactory;
import com.openwebstart.controlpanel.FormPanel;
import com.openwebstart.jvm.JavaRuntimeManager;
import com.openwebstart.jvm.RuntimeManagerConfig;
import com.openwebstart.jvm.RuntimeUpdateStrategy;
import com.openwebstart.jvm.ui.LookAndFeel;
import com.openwebstart.ui.ModalDialog;
import com.openwebstart.ui.TranslatableEnumComboboxRenderer;
import net.adoptopenjdk.icedteaweb.StringUtils;
import net.adoptopenjdk.icedteaweb.client.util.UiLock;
import net.adoptopenjdk.icedteaweb.i18n.Translator;
import net.adoptopenjdk.icedteaweb.logging.Logger;
import net.adoptopenjdk.icedteaweb.logging.LoggerFactory;
import net.adoptopenjdk.icedteaweb.os.OsUtil;
import net.sourceforge.jnlp.config.DeploymentConfiguration;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.NumberFormatter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.openwebstart.concurrent.ThreadPoolHolder.getDaemonExecutorService;
import static com.openwebstart.config.OwsDefaultsProvider.ALLOW_DOWNLOAD_SERVER_FROM_JNLP;
import static com.openwebstart.config.OwsDefaultsProvider.ALLOW_VENDOR_FROM_JNLP;
import static com.openwebstart.config.OwsDefaultsProvider.DEFAULT_JVM_DOWNLOAD_SERVER;
import static com.openwebstart.config.OwsDefaultsProvider.JVM_UPDATE_STRATEGY;
import static com.openwebstart.config.OwsDefaultsProvider.JVM_VENDOR;
import static com.openwebstart.config.OwsDefaultsProvider.MAX_DAYS_UNUSED_IN_JVM_CACHE;
import static java.awt.Cursor.WAIT_CURSOR;
import static java.awt.Cursor.getDefaultCursor;
import static java.awt.Cursor.getPredefinedCursor;

public class ConfigurationDialog extends ModalDialog {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationDialog.class);

    private final Translator translator = Translator.getInstance();
    private final JComboBox<String> vendorComboBox;
    private final Color originalBackground;
    private boolean urlValidationError = false;
    private final JButton okButton;

    public ConfigurationDialog(final DeploymentConfiguration deploymentConfiguration) {
        setTitle(translator.translate("dialog.jvmManagerConfig.title"));

        final UiLock uiLock = new UiLock(deploymentConfiguration);

        final JLabel updateStrategyLabel = new JLabel(translator.translate("dialog.jvmManagerConfig.updateStrategy.text"));
        final JComboBox<RuntimeUpdateStrategy> updateStrategyComboBox = new JComboBox<>(RuntimeUpdateStrategy.values());
        updateStrategyComboBox.setRenderer(new TranslatableEnumComboboxRenderer<>());
        updateStrategyComboBox.setSelectedItem(RuntimeManagerConfig.getStrategy());
        uiLock.update(JVM_UPDATE_STRATEGY, updateStrategyComboBox);

        final JLabel defaultVendorLabel = new JLabel(translator.translate("dialog.jvmManagerConfig.vendor.text"));
        vendorComboBox = new JComboBox<>();
        getDaemonExecutorService().execute(() -> updateVendorComboBox(RuntimeManagerConfig.getDefaultRemoteEndpoint()));
        vendorComboBox.setEditable(true);
        uiLock.update(JVM_VENDOR, vendorComboBox);

        final JCheckBox allowVendorFromJnlpCheckBox = new JCheckBox(translator.translate("dialog.jvmManagerConfig.allowVendorFromJnlp.text"));
        allowVendorFromJnlpCheckBox.setSelected(RuntimeManagerConfig.isVendorFromJnlpAllowed());
        uiLock.update(ALLOW_VENDOR_FROM_JNLP, allowVendorFromJnlpCheckBox);

        final JLabel defaultUpdateServerLabel = new JLabel(translator.translate("dialog.jvmManagerConfig.defaultServerUrl.text"));
        final JTextField defaultUpdateServerField = new JTextField();
        originalBackground = defaultUpdateServerField.getBackground();
        try {
            defaultUpdateServerField.setText(Optional.ofNullable(RuntimeManagerConfig.getDefaultRemoteEndpoint()).map(URL::toString).orElse(""));
        } catch (final Exception e) {
            LOG.error("Can not set default server url!", e);
        }
        defaultUpdateServerField.addFocusListener(new MyFocusAdapter());
        uiLock.update(DEFAULT_JVM_DOWNLOAD_SERVER, defaultUpdateServerField);


        final JCheckBox allowAnyUpdateServerCheckBox = new JCheckBox(translator.translate("dialog.jvmManagerConfig.allowServerInJnlp.text"));
        allowAnyUpdateServerCheckBox.setSelected(RuntimeManagerConfig.isNonDefaultServerAllowed());
        uiLock.update(ALLOW_DOWNLOAD_SERVER_FROM_JNLP, allowAnyUpdateServerCheckBox);

        final JLabel unusedRuntimeCleanupLabel = new JLabel(translator.translate("dialog.jvmManagerConfig.unusedRuntimeCleanup.text"));
        final JFormattedTextField maxDaysStayInJvmCacheField = getMaxDaysInJvmCacheField();
        uiLock.update(MAX_DAYS_UNUSED_IN_JVM_CACHE, maxDaysStayInJvmCacheField);
        try {
            maxDaysStayInJvmCacheField.setText(Optional.of(RuntimeManagerConfig.getMaxDaysUnusedInJvmCache() + "").orElse("0"));
        } catch (final Exception e) {
            LOG.error("Can not set default for max days unused in JVM cache!", e);
        }
        final JPanel numberOfDaysPanel = new JPanel();
        numberOfDaysPanel.setLayout(new BorderLayout());
        numberOfDaysPanel.add(maxDaysStayInJvmCacheField, BorderLayout.WEST);
        numberOfDaysPanel.add(new JLabel(translator.translate("dialog.jvmManagerConfig.unusedRuntimeCleanup.days.text")), BorderLayout.CENTER);

        okButton = new JButton(translator.translate("action.ok"));
        okButton.addActionListener(e -> {
            try {
                if (urlValidationError) {
                    defaultUpdateServerField.requestFocus();
                    return;
                }
                RuntimeManagerConfig.setStrategy((RuntimeUpdateStrategy) updateStrategyComboBox.getSelectedItem());
                RuntimeManagerConfig.setDefaultVendor((String) vendorComboBox.getSelectedItem());
                RuntimeManagerConfig.setVendorFromJnlpAllowed(allowVendorFromJnlpCheckBox.isSelected());
                RuntimeManagerConfig.setDefaultRemoteEndpoint(new URL(defaultUpdateServerField.getText()));
                RuntimeManagerConfig.setNonDefaultServerAllowed(allowAnyUpdateServerCheckBox.isSelected());
                try {
                    maxDaysStayInJvmCacheField.commitEdit();
                } catch (ParseException ex) {
                    LOG.error("Error in UI field.", ex);
                    RuntimeManagerConfig.setMaxDaysUnusedInJvmCache(String.valueOf(RuntimeManagerConfig.getMaxDaysUnusedInJvmCache()));
                }
                RuntimeManagerConfig.setMaxDaysUnusedInJvmCache(maxDaysStayInJvmCacheField.getValue().toString());
                close();
            } catch (final MalformedURLException ex) {
                DialogFactory.showErrorDialog(translator.translate("jvmManager.error.invalidServerUri"), ex);
            }
        });

        JButton cancelButton = new JButton(translator.translate("action.cancel"));
        cancelButton.addActionListener(e -> close());

        final FormPanel mainPanel = new FormPanel();
        mainPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        mainPanel.addRow(0, updateStrategyLabel, updateStrategyComboBox);
        mainPanel.addRow(1, defaultUpdateServerLabel, defaultUpdateServerField);
        mainPanel.addEditorRow(2, allowAnyUpdateServerCheckBox);
        mainPanel.addRow(3, defaultVendorLabel, vendorComboBox);
        mainPanel.addEditorRow(4, allowVendorFromJnlpCheckBox);
        mainPanel.addRow(5, unusedRuntimeCleanupLabel, numberOfDaysPanel);
        mainPanel.addFlexibleRow(6);

        final JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout(8, 8));
        panel.add(mainPanel, BorderLayout.CENTER);
        panel.add(ButtonPanelFactory.createButtonPanel(okButton, cancelButton), BorderLayout.SOUTH);

        add(panel);

        // FIXME: this is a workaround for a look&feel issue introduced with 8_242
        // The numberOfDays was
        if (OsUtil.isLinux()) {
            SwingUtilities.invokeLater(() -> numberOfDaysPanel.setPreferredSize(new Dimension(50, vendorComboBox.getHeight())));
        }
    }

    private JFormattedTextField getMaxDaysInJvmCacheField() {
        NumberFormat format = NumberFormat.getInstance();
        format.setGroupingUsed(false);
        NumberFormatter formatter = new NumberFormatter(format) {
            @Override
            public Object stringToValue(final String text) throws ParseException {
                if (text.length() == 0) {
                    return null;
                }
                return super.stringToValue(text);
            }
        };
        formatter.setValueClass(Integer.class);
        formatter.setMinimum(0);
        formatter.setMaximum(9999);
        formatter.setAllowsInvalid(false);
        formatter.setCommitsOnValidEdit(true);

        final JFormattedTextField maxDaysStayInJvmCacheField = new JFormattedTextField(formatter);
        maxDaysStayInJvmCacheField.setPreferredSize(new Dimension(60, 20));

        maxDaysStayInJvmCacheField.addPropertyChangeListener("value", evt -> {
            if (okButton != null) {
                if (maxDaysStayInJvmCacheField.getText().isEmpty()) {
                    maxDaysStayInJvmCacheField.setBackground(LookAndFeel.FIELD_VALIDATION_PROBLEM);
                    okButton.setEnabled(false);

                } else {
                    maxDaysStayInJvmCacheField.setBackground(originalBackground);
                    okButton.setEnabled(true);
                }
            }
        });
        return maxDaysStayInJvmCacheField;
    }

    private void updateVendorComboBox(final URL specifiedServerURL) {
        try {
            SwingUtilities.invokeLater(() -> vendorComboBox.setCursor(getPredefinedCursor(WAIT_CURSOR)));
            final List<String> vendorNamesList = new ArrayList<>(Arrays.asList(JavaRuntimeManager.getAllVendors(specifiedServerURL)));
            if (!vendorNamesList.contains(RuntimeManagerConfig.getVendor())) {
                vendorNamesList.add(RuntimeManagerConfig.getVendor());
            }
            SwingUtilities.invokeLater(() -> {
                vendorComboBox.setModel(new DefaultComboBoxModel<>(vendorNamesList.toArray(new String[0])));
                vendorComboBox.setSelectedItem(RuntimeManagerConfig.getVendor());
            });
        } catch (final Exception ex) {
            DialogFactory.showErrorDialog(translator.translate("jvmManager.error.updateVendorNames"), ex);
        } finally {
            SwingUtilities.invokeLater(() -> vendorComboBox.setCursor(getDefaultCursor()));
        }
    }

    private class MyFocusAdapter extends FocusAdapter {
        @Override
        public void focusLost(final FocusEvent e) {
            final JTextField field = (JTextField) e.getSource();
            try {
                final String text = field.getText();
                final String urlString = StringUtils.isBlank(text) ? getDefaultServer() : text;
                field.setText(urlString);
                final URL url = new URL(urlString);
                field.setBackground(originalBackground);
                urlValidationError = false;
                getDaemonExecutorService().execute(() -> updateVendorComboBox(url));
                okButton.setEnabled(true);
            } catch (final MalformedURLException exception) {
                field.setBackground(LookAndFeel.FIELD_VALIDATION_PROBLEM);
                urlValidationError = true;
                field.requestFocus();
                okButton.setEnabled(false);
            }
        }

        private String getDefaultServer() {
            return new OwsDefaultsProvider().getDefaults().stream()
                    .filter(d -> Objects.equals(d.getName(), DEFAULT_JVM_DOWNLOAD_SERVER))
                    .map(d -> d.getDefaultValue())
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Could not find default for " + DEFAULT_JVM_DOWNLOAD_SERVER));
        }
    }
}
