package com.mcpscanner.engine;

import java.util.ArrayList;
import java.util.List;

final class PauseGate {

    private final List<ScanTask<?>> parked = new ArrayList<>();
    private boolean paused;
    private boolean released;

    synchronized boolean parkIfPaused(ScanTask<?> task) {
        if (paused) {
            parked.add(task);
        }
        return paused;
    }

    synchronized boolean pause() {
        if (released || paused) {
            return false;
        }
        paused = true;
        return true;
    }

    synchronized boolean paused() {
        return paused;
    }

    synchronized List<ScanTask<?>> resume() {
        paused = false;
        List<ScanTask<?>> waiting = List.copyOf(parked);
        parked.clear();
        return waiting;
    }

    synchronized List<ScanTask<?>> release() {
        released = true;
        return resume();
    }
}
