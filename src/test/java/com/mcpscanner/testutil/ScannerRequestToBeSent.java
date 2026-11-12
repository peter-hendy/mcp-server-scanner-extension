package com.mcpscanner.testutil;

import burp.api.montoya.core.Annotations;
import burp.api.montoya.core.ToolSource;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.message.HttpHeader;
import com.mcpscanner.mcp.ScannerSentinels;

import java.net.URI;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class ScannerRequestToBeSent {

    private ScannerRequestToBeSent() {
    }

    public static HttpRequestToBeSent sentinelCarryingScannerRequestTo(URI target) {
        HttpService service = HttpService.httpService(target.getHost(), effectivePort(target),
                "https".equalsIgnoreCase(target.getScheme()));
        ToolSource scanner = scannerToolSource();
        HttpHeader sentinel = stripAuthSentinel();
        HttpRequestToBeSent request = mock(HttpRequestToBeSent.class);
        when(request.httpService()).thenReturn(service);
        when(request.pathWithoutQuery()).thenReturn(target.getPath());
        when(request.headers()).thenReturn(List.of(sentinel));
        when(request.httpVersion()).thenReturn("HTTP/1.1");
        when(request.toolSource()).thenReturn(scanner);
        when(request.annotations()).thenReturn(mock(Annotations.class));
        when(request.withService(any(HttpService.class))).thenReturn(request);
        when(request.withHeader(anyString(), anyString())).thenReturn(request);
        return request;
    }

    private static ToolSource scannerToolSource() {
        ToolSource scanner = mock(ToolSource.class);
        when(scanner.isFromTool(ToolType.SCANNER)).thenReturn(true);
        return scanner;
    }

    private static HttpHeader stripAuthSentinel() {
        HttpHeader sentinel = mock(HttpHeader.class);
        when(sentinel.name()).thenReturn(ScannerSentinels.STRIP_AUTH_HEADER);
        when(sentinel.value()).thenReturn("1");
        return sentinel;
    }

    private static int effectivePort(URI target) {
        if (target.getPort() != -1) return target.getPort();
        return "https".equalsIgnoreCase(target.getScheme()) ? 443 : 80;
    }
}
