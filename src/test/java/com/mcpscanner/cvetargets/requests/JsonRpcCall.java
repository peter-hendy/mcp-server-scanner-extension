package com.mcpscanner.cvetargets.requests;

import com.fasterxml.jackson.databind.node.ObjectNode;

public record JsonRpcCall(String method, ObjectNode params) {
}
