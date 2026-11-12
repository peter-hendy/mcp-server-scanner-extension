package com.mcpscanner.checks.shared.oauth;

import java.util.Optional;

public record CallbackResponse(Optional<String> code) {

    public boolean deliversCode() {
        return code.isPresent();
    }
}
