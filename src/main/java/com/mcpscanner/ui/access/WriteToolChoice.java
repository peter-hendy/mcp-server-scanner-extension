package com.mcpscanner.ui.access;

import com.mcpscanner.access.mining.WriteToolOptIn;

enum WriteToolChoice {
    READ_ONLY("Read-only tools only", WriteToolOptIn.Mode.NONE),
    ALL("All tools", WriteToolOptIn.Mode.ALL),
    SELECTED("Selected tools", WriteToolOptIn.Mode.SELECTED);

    private final String label;
    private final WriteToolOptIn.Mode mode;

    WriteToolChoice(String label, WriteToolOptIn.Mode mode) {
        this.label = label;
        this.mode = mode;
    }

    static WriteToolChoice of(WriteToolOptIn.Mode mode) {
        for (WriteToolChoice choice : values()) {
            if (choice.mode == mode) {
                return choice;
            }
        }
        return READ_ONLY;
    }

    @Override
    public String toString() {
        return label;
    }
}
