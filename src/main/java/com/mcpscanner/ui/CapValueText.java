package com.mcpscanner.ui;

import com.mcpscanner.config.CapUnit;
import com.mcpscanner.config.ResourceCap;

import java.util.OptionalLong;

interface CapValueText {

    String format(long value);

    OptionalLong parse(String typed);

    String invalidHint(ResourceCap cap);

    static CapValueText of(CapUnit unit) {
        return switch (unit) {
            case BYTES -> new ByteSizeText();
            case COUNT -> new CountText();
        };
    }
}
