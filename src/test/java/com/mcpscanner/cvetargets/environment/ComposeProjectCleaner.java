package com.mcpscanner.cvetargets.environment;

import java.time.Duration;
import java.util.List;

public final class ComposeProjectCleaner {

    private static final int DEFAULT_ATTEMPTS = 30;
    private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(2);

    private final Pause pause;
    private final int attempts;
    private final Duration interval;

    public ComposeProjectCleaner(Pause pause, int attempts, Duration interval) {
        this.pause = pause;
        this.attempts = attempts;
        this.interval = interval;
    }

    public static ComposeProjectCleaner standard() {
        return new ComposeProjectCleaner(Pause.sleeping(), DEFAULT_ATTEMPTS, DEFAULT_INTERVAL);
    }

    public void clean(DockerCompose compose) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                compose.down();
                List<String> leftovers = compose.containers();
                if (leftovers.isEmpty()) {
                    return;
                }
                lastFailure = new EnvironmentException("containers left after teardown: " + leftovers);
            } catch (EnvironmentException dockerStillRemoving) {
                lastFailure = dockerStillRemoving;
            }
            if (attempt < attempts) {
                pause.pause(interval);
            }
        }
        throw lastFailure;
    }
}
