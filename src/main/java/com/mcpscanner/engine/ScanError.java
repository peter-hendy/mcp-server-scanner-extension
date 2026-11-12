package com.mcpscanner.engine;

import java.time.Instant;
import java.util.Optional;

public record ScanError(Instant at, Optional<String> check, String requestUrl, Optional<String> field,
                        String reason) {
}
