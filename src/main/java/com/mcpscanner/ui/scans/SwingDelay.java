package com.mcpscanner.ui.scans;

import javax.swing.Timer;

public final class SwingDelay implements Delay {

    @Override
    public void after(long millis, Runnable task) {
        Timer timer = new Timer((int) millis, event -> task.run());
        timer.setRepeats(false);
        timer.start();
    }
}
