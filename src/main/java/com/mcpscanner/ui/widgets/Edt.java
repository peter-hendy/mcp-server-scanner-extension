package com.mcpscanner.ui.widgets;

import javax.swing.SwingUtilities;

public final class Edt {

    private Edt() {
    }

    public static void run(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }
}
