package com.mcpscanner.engine;

import java.util.Optional;

public record ScanCredit(long scanId, Optional<String> testedRequest) {
}
