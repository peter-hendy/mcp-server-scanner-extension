package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

record ClockReading(boolean followsClock, String description) {

    static ClockReading notFollowed() {
        return new ClockReading(false, "Its timestamp field does not match the time the sessions were issued, so "
                + "the scanner counted the bits that varied in it as unpredictable.");
    }
}
