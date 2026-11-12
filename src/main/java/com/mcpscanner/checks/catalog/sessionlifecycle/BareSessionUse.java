package com.mcpscanner.checks.catalog.sessionlifecycle;

import burp.api.montoya.http.message.HttpRequestResponse;

record BareSessionUse(HttpRequestResponse refusedWithoutSession, HttpRequestResponse anonymousInitializeRefused,
                      HttpRequestResponse unissuedIdRefused, HttpRequestResponse answeredWithSessionOnly) {
}
