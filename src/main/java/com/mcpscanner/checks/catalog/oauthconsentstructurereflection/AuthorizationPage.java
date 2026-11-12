package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.checks.shared.html.HtmlDocument;
import com.mcpscanner.checks.shared.oauth.RedirectStatus;
import com.mcpscanner.checks.shared.oauth.ResponseHeaders;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

record AuthorizationPage(URI url, List<HttpRequestResponse> exchanges) {

    AuthorizationPage {
        exchanges = List.copyOf(exchanges);
    }

    HttpRequestResponse finalExchange() {
        return exchanges.get(exchanges.size() - 1);
    }

    HtmlDocument document() {
        HttpResponse response = finalExchange().response();
        boolean rendered = !RedirectStatus.isRedirect(response.statusCode()) && ResponseHeaders.isHtml(response);
        return HtmlDocument.parse(rendered ? response.bodyToString() : "");
    }

    List<String> enforcedContentSecurityPolicies() {
        return Stream.concat(
                ResponseHeaders.values(finalExchange().response(), "Content-Security-Policy").stream(),
                document().metaContentSecurityPolicies().stream()).toList();
    }

}
