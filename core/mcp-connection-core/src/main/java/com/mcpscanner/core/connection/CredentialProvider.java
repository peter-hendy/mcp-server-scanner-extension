package com.mcpscanner.core.connection;

import java.util.Map;

public interface CredentialProvider {
    Map<String, String> headers();
    default java.util.Set<String> headerNames() { return java.util.Set.of("Authorization", "Cookie", "Proxy-Authorization"); }
    default OperationRecovery beginOperation(String operation) { return response -> RecoveryResult.STOP; }
}
