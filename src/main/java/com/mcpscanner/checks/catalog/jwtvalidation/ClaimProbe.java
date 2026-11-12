package com.mcpscanner.checks.catalog.jwtvalidation;

import burp.api.montoya.http.message.HttpRequestResponse;

record ClaimProbe(ClaimMutation mutation, HttpRequestResponse exchange, boolean accepted) {
}
