package com.mcpscanner.checks.api;

import java.time.Duration;
import java.util.function.BooleanSupplier;

@FunctionalInterface
interface RetryWait {

    boolean waitedOut(Duration wait, BooleanSupplier stillWanted) throws InterruptedException;
}
