package com.mcpscanner.engine;

import java.util.List;

public record ScanErrors(List<ScanError> recent, int total) {

    static final int KEPT = 50;
}
