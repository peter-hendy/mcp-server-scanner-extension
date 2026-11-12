package com.mcpscanner.checks.catalog.filetraversal;

import com.mcpscanner.testutil.ImmutableHttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OperationLabelTest {

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void namesTheMethodAndItsToolPromptOrResource() {
        assertThat(label("{\"method\":\"tools/call\",\"params\":{\"name\":\"read\"}}")).isEqualTo("tools/call read");
        assertThat(label("{\"method\":\"resources/read\",\"params\":{\"uri\":\"file:///a\"}}"))
                .isEqualTo("resources/read file://");
        assertThat(label("{\"method\":\"resources/read\",\"params\":{\"uri\":\"urn:x:y\"}}"))
                .isEqualTo("resources/read urn:");
        assertThat(label("{\"method\":\"ping\"}")).isEqualTo("ping");
        assertThat(label("nope")).isEqualTo("unparseable request");
    }

    private static String label(String body) {
        return OperationLabel.of(ImmutableHttpRequest.post("http://h/mcp", Map.of(), body));
    }
}
