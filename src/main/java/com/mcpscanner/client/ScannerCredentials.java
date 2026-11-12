package com.mcpscanner.client;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.StrategyCredentials;
import com.mcpscanner.core.connection.OperationRecovery;
import com.mcpscanner.core.connection.RecoveryResult;

import java.util.function.BooleanSupplier;

final class ScannerCredentials extends StrategyCredentials {
    private final BooleanSupplier refresh;

    ScannerCredentials(AuthStrategy auth, BooleanSupplier refresh) {
        super(auth);
        this.refresh = refresh;
    }

    @Override
    public OperationRecovery beginOperation(String operation) {
        return new OperationRecovery() {
            private boolean attempted;

            @Override
            public RecoveryResult recover(com.mcpscanner.core.protocol.McpFlow.HttpResponse response) {
                if (response.status() != 401 || attempted) return RecoveryResult.STOP;
                attempted = true;
                return refresh.getAsBoolean() ? RecoveryResult.RETRY : RecoveryResult.STOP;
            }
        };
    }
}
