package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import burp.api.montoya.http.message.HttpRequestResponse;

record JudgedToken(Usability usability, HttpRequestResponse candidate) {
}
