package com.mcpscanner.testutil;

import burp.api.montoya.http.Http;
import com.mcpscanner.client.BurpProtocolHttpTransport;
import com.mcpscanner.client.McpClientManager;
import com.mcpscanner.client.ProtocolHttpTransport;
import com.mcpscanner.config.ClientIdentityProvider;
import com.mcpscanner.logging.McpEventLog;

import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Optional;

public final class MutualTlsFixtures {

    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(5);

    private MutualTlsFixtures() {
    }

    public static McpClientManager managerThrough(McpEventLog eventLog, ProtocolHttpTransport transport) {
        return new McpClientManager(eventLog, ClientIdentityProvider.defaults(), transport);
    }

    public static ProtocolHttpTransport burpTransport(Http burpHttp) {
        return new BurpProtocolHttpTransport(burpHttp);
    }

    public static HttpClient jdkClientTrustingCa(TestPki pki) throws GeneralSecurityException {
        return HttpClient.newBuilder()
                .sslContext(pki.clientTrustingCa(Optional.empty()))
                .connectTimeout(HANDSHAKE_TIMEOUT)
                .build();
    }
}
