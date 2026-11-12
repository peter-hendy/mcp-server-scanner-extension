package com.mcpscanner.checks.shared.collaborator;

import java.time.Duration;

@FunctionalInterface
public interface Pause {

    void pause(Duration duration);

    static Pause sleeping() {
        return duration -> {
            try {
                Thread.sleep(duration.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }
}
