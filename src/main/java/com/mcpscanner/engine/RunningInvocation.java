package com.mcpscanner.engine;

import java.util.Optional;

public record RunningInvocation(String check, String request, Optional<String> field) {
}
