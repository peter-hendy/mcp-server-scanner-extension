package com.mcpscanner.client;

import com.mcpscanner.core.connection.ConnectionAuthException;
import com.mcpscanner.core.connection.CredentialProvider;
import com.mcpscanner.core.connection.OperationRecovery;
import com.mcpscanner.core.connection.RecoveryResult;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

final class GuardedCredentials implements CredentialProvider {
    private final CredentialProvider delegate;
    private boolean refusedCredentials;

    GuardedCredentials(CredentialProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public Map<String, String> headers() {
        return invoke(delegate::headers);
    }

    @Override
    public Set<String> headerNames() {
        return invoke(delegate::headerNames);
    }

    @Override
    public OperationRecovery beginOperation(String operation) {
        OperationRecovery recovery = invoke(() -> delegate.beginOperation(operation));
        return response -> invoke(() -> recovery.recover(response));
    }

    boolean hasRefusedCredentials() {
        return refusedCredentials;
    }

    private <T> T invoke(Supplier<T> action) {
        try {
            return action.get();
        } catch (ConnectionAuthException failure) {
            refusedCredentials = true;
            throw failure;
        } catch (RuntimeException failure) {
            refusedCredentials = true;
            throw new ConnectionAuthException(RecoveryResult.STOP);
        }
    }
}
