package com.openwebstart.jvm.ui;

import com.openwebstart.jvm.runtimes.LocalJavaRuntime;

import javax.swing.DefaultListModel;
import java.util.List;

public class RuntimeListModel extends DefaultListModel<LocalJavaRuntime> {

    public void replaceData(final List<LocalJavaRuntime> loadedData) {
        clear();
        loadedData.forEach(item -> addElement(item));
    }

    public void replaceItem(final LocalJavaRuntime oldValue, final LocalJavaRuntime newValue) {
        final int index = indexOf(oldValue);
        remove(index);
        add(index, newValue);
    }
}
