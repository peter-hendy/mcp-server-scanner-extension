package com.mcpscanner.engine;

import java.util.concurrent.ExecutorService;

public interface EngineWorkers {

    ExecutorService executor();

    void applyWorkerCount();
}
