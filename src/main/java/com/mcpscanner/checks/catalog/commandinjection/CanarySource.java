package com.mcpscanner.checks.catalog.commandinjection;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

@FunctionalInterface
interface CanarySource {

    long LOWEST_OPERAND = 100_000_000L;
    long OPERAND_SPAN = 800_000_000L;

    ArithmeticCanary next();

    static CanarySource random() {
        RandomGenerator random = new SecureRandom();
        return () -> new ArithmeticCanary(operand(random), operand(random));
    }

    private static long operand(RandomGenerator random) {
        return LOWEST_OPERAND + random.nextLong(OPERAND_SPAN);
    }
}
