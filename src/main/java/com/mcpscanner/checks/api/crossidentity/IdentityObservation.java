package com.mcpscanner.checks.api.crossidentity;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.api.Principal;

import java.util.List;

public record IdentityObservation(Principal principal, String family, String text,
                                  List<HttpRequestResponse> evidence) {
}
