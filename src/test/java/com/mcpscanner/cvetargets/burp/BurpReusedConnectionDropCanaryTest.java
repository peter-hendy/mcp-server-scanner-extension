package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.automation.ScanAllCommand;
import com.mcpscanner.client.McpFixtureProfile;
import com.mcpscanner.client.McpFixtureServer;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.cvetargets.environment.Pause;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Tag("burp-canary")
class BurpReusedConnectionDropCanaryTest {

    private static McpFixtureServer server;
    private static ReusedConnectionDropper dropper;
    private static HeadlessBurpSession burp;

    @BeforeAll
    static void startBurpBehindADroppingRelay() throws IOException {
        server = new McpFixtureServer(McpFixtureProfile.streamable("2025-06-18"));
        dropper = ReusedConnectionDropper.inFrontOf(server.endpoint());
        burp = new HeadlessBurpLauncher(BurpSettings.fromSystemProperties(), Pause.sleeping())
                .start("reused-connection-drop-canary", dropper.endpoint(), () -> false);
    }

    @AfterAll
    static void stopEverything() {
        if (burp != null) {
            burp.close();
        }
        dropper.close();
        server.close();
    }

    @Test
    void connectThroughBurpSurvivesAServerThatClosesEveryReusedConnection() {
        ScanAllCommand connectOnly = new ScanAllCommand(dropper.endpoint(), TransportType.STREAMABLE_HTTP, null,
                Set.of(), List.of(), true, false, null);

        assertThatCode(() -> burp.automation().scanAll(connectOnly)).doesNotThrowAnyException();
        System.out.printf("Relay through Burp: %d requests relayed, %d reused connections dropped%n",
                dropper.relayed(), dropper.dropped());
        assertThat(dropper.dropped()).as("reused connections the relay dropped").isPositive();
    }
}
