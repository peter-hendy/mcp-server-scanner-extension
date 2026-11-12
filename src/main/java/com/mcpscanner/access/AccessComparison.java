package com.mcpscanner.access;

import burp.api.montoya.http.Http;

public interface AccessComparison {

    AccessComparisonRun begin(Http http);
}
