package com.mcpscanner.ui.sendto;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.function.Supplier;

public record SendToRequest(String title, Supplier<HttpRequest> request) {
}
