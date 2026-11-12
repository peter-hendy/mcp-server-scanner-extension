package com.mcpscanner.checks.shared.field;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import com.mcpscanner.mcp.BurpNativeJsonRpcFields;
import com.mcpscanner.testutil.BurpArgumentPoint;
import com.mcpscanner.testutil.ImmutableHttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InsertionPointNamesTest {

    private static final HttpRequest COPY = ImmutableHttpRequest.post("http://mcp.example/mcp",
            Map.of("Content-Type", "application/json"),
            "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"copy_file\","
                    + "\"arguments\":{\"source\":{\"path\":\"a.txt\"},\"dest\":{\"path\":\"b.txt\"},"
                    + "\"retries\":2,\"tags\":[\"x\",\"y\"]}}}");

    private final InsertionPointNames besideNativeFields = new InsertionPointNames(BurpNativeJsonRpcFields.PRESENT);
    private final InsertionPointNames withoutNativeFields = new InsertionPointNames(BurpNativeJsonRpcFields.ABSENT);

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void namesSameKeyedBurpArgumentPointsByTheirFullPaths() {
        assertThat(besideNativeFields.nameOf(COPY, new BurpArgumentPoint(COPY, "arguments", "source", "path")))
                .isEqualTo("MCP field: params.arguments.source.path");
        assertThat(besideNativeFields.nameOf(COPY, new BurpArgumentPoint(COPY, "arguments", "dest", "path")))
                .isEqualTo("MCP field: params.arguments.dest.path");
    }

    @Test
    void findsANumericArgument() {
        assertThat(besideNativeFields.burpArgumentNameOf(COPY, new BurpArgumentPoint(COPY, "arguments", "retries")))
                .contains("MCP field: params.arguments.retries");
    }

    @Test
    void keepsTheOwnNameOfAPointBurpDoesNotFuzzAsAnArgument() {
        AuditInsertionPoint name = new BurpArgumentPoint(COPY, "name");

        assertThat(besideNativeFields.burpArgumentNameOf(COPY, name)).isEmpty();
        assertThat(besideNativeFields.nameOf(COPY, name)).isEqualTo("name");
    }

    @Test
    void keepsBurpsBareNameWhenBurpHasNoNativeFields() {
        AuditInsertionPoint source = new BurpArgumentPoint(COPY, "arguments", "source", "path");

        assertThat(withoutNativeFields.burpArgumentNameOf(COPY, source)).isEmpty();
        assertThat(withoutNativeFields.nameOf(COPY, source)).isEqualTo("path");
    }

    @Test
    void keepsTheExtensionsOwnFieldNames() {
        AuditInsertionPoint ours = mock(AuditInsertionPoint.class);
        when(ours.name()).thenReturn("MCP field: params.arguments.source.path");

        assertThat(besideNativeFields.nameOf(COPY, ours)).isEqualTo("MCP field: params.arguments.source.path");
    }
}
