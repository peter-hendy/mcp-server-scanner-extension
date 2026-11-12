package com.mcpscanner.testutil;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.internal.ObjectFactoryLocator;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.when;

public final class BurpBrowserTemplate {

    public static final Map<String, String> DEFAULT_HEADERS = defaultHeaders();

    private BurpBrowserTemplate() {
    }

    public static void installFor(String url) {
        HttpRequest template = HttpRequest.httpRequestFromUrl(url)
                .withAddedHeader("Host", URI.create(url).getRawAuthority());
        for (Map.Entry<String, String> header : DEFAULT_HEADERS.entrySet()) {
            template = template.withAddedHeader(header.getKey(), header.getValue());
        }
        when(ObjectFactoryLocator.FACTORY.httpRequestFromUrl(url)).thenReturn(template);
    }

    private static Map<String, String> defaultHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Cache-Control", "max-age=0");
        headers.put("Sec-Ch-Ua", "\"Google Chrome\";v=\"156\", \"Not=A?Brand\";v=\"8\", \"Chromium\";v=\"156\"");
        headers.put("Sec-Ch-Ua-Mobile", "?0");
        headers.put("Sec-Ch-Ua-Platform", "\"macOS\"");
        headers.put("Accept-Language", "en-US;q=0.9,en;q=0.8");
        headers.put("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        headers.put("Sec-Fetch-Site", "none");
        headers.put("Sec-Fetch-Mode", "navigate");
        headers.put("Sec-Fetch-User", "?1");
        headers.put("Sec-Fetch-Dest", "document");
        headers.put("Accept-Encoding", "gzip, deflate, br");
        headers.put("Connection", "close");
        return headers;
    }
}
