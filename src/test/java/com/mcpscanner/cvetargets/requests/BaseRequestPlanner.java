package com.mcpscanner.cvetargets.requests;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.cvetargets.manifest.SeedRequest;
import com.mcpscanner.mcp.McpOperationFraming;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface BaseRequestPlanner {

    List<HttpRequest> plan(ConnectResult inventory, List<SeedRequest> seeds, String endpoint,
                           Map<String, String> sessionHeaders, McpOperationFraming.Framer framer);
}
