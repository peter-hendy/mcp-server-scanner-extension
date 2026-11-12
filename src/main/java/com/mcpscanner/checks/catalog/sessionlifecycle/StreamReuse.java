package com.mcpscanner.checks.catalog.sessionlifecycle;

import burp.api.montoya.http.message.HttpRequestResponse;

record StreamReuse(HttpRequestResponse openedAfterDelete, HttpRequestResponse unissuedIdRejected) {
}
