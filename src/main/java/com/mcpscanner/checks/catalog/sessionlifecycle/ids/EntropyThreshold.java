package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

final class EntropyThreshold {

    static final int MINIMUM_BITS = 64;
    private static final double SIGMAS = 6;

    private EntropyThreshold() {
    }

    static Judgement countedBits(int bits, int samples, String weakness) {
        if (bits >= MINIMUM_BITS) {
            return Judgement.sound();
        }
        return bits < MINIMUM_BITS - missTolerance(samples) ? Judgement.weak(weakness) : Judgement.undecided();
    }

    static Judgement estimatedBits(double bits, String weakness) {
        return bits < MINIMUM_BITS ? Judgement.weak(weakness) : Judgement.sound();
    }

    private static double missTolerance(int samples) {
        double missed = Math.pow(2, 1 - samples);
        double expectedMisses = MINIMUM_BITS * missed;
        return expectedMisses + SIGMAS * Math.sqrt(expectedMisses * (1 - missed));
    }
}
