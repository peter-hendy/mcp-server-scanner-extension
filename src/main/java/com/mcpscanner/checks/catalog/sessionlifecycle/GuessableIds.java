package com.mcpscanner.checks.catalog.sessionlifecycle;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.util.List;
import java.util.Optional;

record GuessableIds(List<String> ids, List<String> weaknesses, List<HttpRequestResponse> handshakes,
                    Optional<BareSessionUse> bareUse, boolean liveSessionReissued) {
}
