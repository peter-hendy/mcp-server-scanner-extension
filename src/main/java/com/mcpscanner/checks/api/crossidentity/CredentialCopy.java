package com.mcpscanner.checks.api.crossidentity;

import com.mcpscanner.checks.api.Principal;

public record CredentialCopy(Principal copy, Principal original) {
}
