package com.mcpscanner.checks.shared.collaborator;

import java.time.Clock;
import java.util.Objects;

public record WatchTiming(Clock clock, Pause pause, InteractionWindow defaultWindow) {

    public WatchTiming {
        Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(pause, "pause must not be null");
        Objects.requireNonNull(defaultWindow, "defaultWindow must not be null");
    }

    public static WatchTiming standard() {
        return new WatchTiming(Clock.systemUTC(), Pause.sleeping(), InteractionWindow.standard());
    }
}
