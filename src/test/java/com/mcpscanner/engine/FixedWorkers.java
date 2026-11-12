package com.mcpscanner.engine;

import java.util.concurrent.ExecutorService;

record FixedWorkers(ExecutorService executor) implements EngineWorkers {

    @Override
    public void applyWorkerCount() {
    }
}
