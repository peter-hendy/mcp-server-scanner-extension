package com.mcpscanner.checks.shared.auth.oracle;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.mcpscanner.checks.shared.auth.oracle.Exchanges.toolCall;
import static org.assertj.core.api.Assertions.assertThat;

class CredentialRequirementRefusalTest {

    private final BaselineEquivalenceOracle oracle = BaselineEquivalenceOracle.defaultOracle();

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "This tool is not available. This tool requires a valid Terraform Cloud/Enterprise token and "
                    + "configuration. Please set TFE_TOKEN.",
            "failed to list Terraform organizations: unauthorized",
            "Authentication required: please provide a bearer token",
            "Missing bearer token",
            "Invalid or expired token",
            "Invalid API key provided",
            "No credentials were supplied with this request",
            "Not authenticated. Set the X-Api-Key header.",
            "This endpoint requires authentication",
            "Error: missing Authorization header"})
    void toolErrorStatingACredentialRequirementIsRefused(String text) {
        assertThat(oracle.refused(toolCall(200, toolError(text)))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Tool execution failed: this tool requires a valid access token",
            "Invalid session token supplied"})
    void jsonRpcErrorStatingACredentialRequirementIsRefused(String text) {
        assertThat(oracle.refused(toolCall(200, rpcError(text)))).isTrue();
    }

    @Test
    void credentialRequirementInTheErrorDataIsRead() {
        ObjectNode reply = reply();
        reply.putObject("error").put("code", -32603).put("message", "Internal error")
                .putObject("data").put("detail", "Missing API key");

        assertThat(oracle.refused(toolCall(200, reply.toString()))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "EACCES: permission denied, open '/etc/shadow'",
            "SyntaxError: invalid token",
            "Unexpected token } in JSON at position 5",
            "max_tokens must be at most 4096",
            "Token limit exceeded for this request",
            "Missing required argument: api_key",
            "This tool requires the api_key argument",
            "Invalid api_key parameter: expected a string",
            "File not found: credentials.json",
            "HTTP 401 Unauthorized fetching https://example.com/data",
            "Request to https://example.com returned 401 Unauthorized",
            "The authorization header guide is unavailable",
            "Invalid params: expected a string"})
    void ordinaryToolErrorsAreNotRefusals(String text) {
        assertThat(oracle.refused(toolCall(200, toolError(text)))).isFalse();
        assertThat(oracle.refused(toolCall(200, rpcError(text)))).isFalse();
    }

    @Test
    void successfulResultStatingARequirementIsNotReadAsAToolError() {
        String text = "Setup guide: this integration requires a valid API key";

        assertThat(oracle.refused(toolCall(200, toolResult(text, false)))).isFalse();
    }

    private static String toolError(String text) {
        return toolResult(text, true);
    }

    private static String toolResult(String text, boolean isError) {
        ObjectNode reply = reply();
        ObjectNode result = reply.putObject("result");
        result.putArray("content").addObject().put("type", "text").put("text", text);
        result.put("isError", isError);
        return reply.toString();
    }

    private static String rpcError(String text) {
        ObjectNode reply = reply();
        reply.putObject("error").put("code", -32000).put("message", text);
        return reply.toString();
    }

    private static ObjectNode reply() {
        return McpObjectMapper.INSTANCE.createObjectNode().put("jsonrpc", "2.0").put("id", 7);
    }
}
