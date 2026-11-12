package com.mcpscanner.cvetargets.environment;

import java.time.Duration;

@FunctionalInterface
public interface Pause {

    void pause(Duration duration);

    static Pause sleeping() {
        return duration -> {
            try {
                Thread.sleep(duration.toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new EnvironmentException("interrupted while waiting");
            }
        };
    }
}
