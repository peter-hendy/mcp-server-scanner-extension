package com.mcpscanner.identity.credentials;

import com.mcpscanner.identity.IdentityRegistry;
import com.mcpscanner.logging.McpEventLog;

import java.util.concurrent.ExecutorService;

public record IdentityCredentialServices(IdentityCredentials credentials, SignInQueue signIns,
                                         PrimaryUserCredentials primaryCredentials) implements AutoCloseable {

    public static IdentityCredentialServices create(IdentityRegistry registry,
                                                    SharedAuthSettings sharedOAuthClient,
                                                    IdentitySignIn signIn, ExecutorService signInWorker,
                                                    McpEventLog eventLog) {
        IdentityCredentials credentials = new IdentityCredentials(registry, sharedOAuthClient);
        SignInQueue signIns = new SignInQueue(registry, credentials, signIn, signInWorker, eventLog);
        return new IdentityCredentialServices(credentials, signIns,
                new PrimaryUserCredentials(registry, credentials, signIns::signedInElsewhere));
    }

    @Override
    public void close() {
        signIns.close();
    }
}
