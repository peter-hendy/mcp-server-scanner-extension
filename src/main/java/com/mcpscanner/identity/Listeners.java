package com.mcpscanner.identity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class Listeners {

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    void add(Runnable listener) {
        listeners.add(listener);
    }

    void remove(Runnable listener) {
        listeners.remove(listener);
    }

    void fire() {
        listeners.forEach(Runnable::run);
    }
}
