package com.mcpscanner.ui.scans;

import com.mcpscanner.engine.ScanSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class FakeScans implements ScanFeed, ScanControls {

    final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    final List<String> calls = new ArrayList<>();
    private volatile List<ScanSnapshot> snapshots = List.of();
    int reads;

    void publish(ScanSnapshot... latest) {
        setSnapshots(latest);
        listeners.forEach(Runnable::run);
    }

    void setSnapshots(ScanSnapshot... latest) {
        snapshots = List.of(latest);
    }

    @Override
    public List<ScanSnapshot> snapshots() {
        reads++;
        return snapshots;
    }

    @Override
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    @Override
    public void pause(long scanId) {
        calls.add("pause " + scanId);
    }

    @Override
    public void resume(long scanId) {
        calls.add("resume " + scanId);
    }

    @Override
    public void cancel(long scanId) {
        calls.add("cancel " + scanId);
    }

    @Override
    public void remove(long scanId) {
        calls.add("remove " + scanId);
    }

    @Override
    public void clearFinished() {
        calls.add("clear finished");
    }
}
