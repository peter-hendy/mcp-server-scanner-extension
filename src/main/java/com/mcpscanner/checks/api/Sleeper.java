package com.mcpscanner.checks.api;

import java.time.Duration;

@FunctionalInterface
interface Sleeper {

    Sleeper THREAD = duration -> Thread.sleep(duration.toMillis());

    void sleep(Duration duration) throws InterruptedException;
}
