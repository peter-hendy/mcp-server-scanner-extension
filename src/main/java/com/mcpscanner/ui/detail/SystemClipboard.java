package com.mcpscanner.ui.detail;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

final class SystemClipboard {

    private SystemClipboard() {
    }

    static void copy(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }
}
