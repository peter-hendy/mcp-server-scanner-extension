package com.mcpscanner.checks.api.crossidentity;

import burp.api.montoya.http.message.requests.HttpRequest;

public record TripletArms(HttpRequest baseline, HttpRequest candidate, HttpRequest control) {
}
