package com.mcpscanner.checks.catalog.downstreamrouteescape;

import burp.api.montoya.http.message.HttpRequestResponse;

@FunctionalInterface
interface ValueSender {

    HttpRequestResponse send(String value);
}
