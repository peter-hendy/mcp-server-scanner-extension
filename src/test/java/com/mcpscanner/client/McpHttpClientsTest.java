package com.mcpscanner.client;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class McpHttpClientsTest {

    @Test
    void upstreamClientNeverFollowsRedirectsOrOffersAnH2cUpgrade() {
        HttpClient client = McpHttpClients.upstream();

        assertThat(client.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
        assertThat(client.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
        assertThat(client.connectTimeout()).contains(Duration.ofSeconds(10));
    }
}
