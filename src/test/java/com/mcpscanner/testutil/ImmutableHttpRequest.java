package com.mcpscanner.testutil;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ImmutableHttpRequest implements InvocationHandler {

    private record Header(String name, String value) implements HttpHeader {
        @Override
        public String toString() {
            return name + ": " + value;
        }
    }

    private record Service(String host, int port, boolean secure) implements HttpService {
        @Override
        public String ipAddress() {
            return host;
        }

        @Override
        public String toString() {
            return (secure ? "https://" : "http://") + host + ":" + port;
        }
    }

    private final String method;
    private final String target;
    private final List<HttpHeader> headers;
    private final String body;
    private final HttpService service;

    private ImmutableHttpRequest(String method, String target, List<HttpHeader> headers, String body,
                                 HttpService service) {
        this.method = method;
        this.target = target;
        this.headers = List.copyOf(headers);
        this.body = body;
        this.service = service;
    }

    public static HttpRequest post(String url, Map<String, String> headers, String body) {
        URI uri = URI.create(url);
        boolean secure = "https".equalsIgnoreCase(uri.getScheme());
        int port = uri.getPort() != -1 ? uri.getPort() : (secure ? 443 : 80);
        String target = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        List<HttpHeader> headerList = new ArrayList<>();
        headerList.add(new Header("Host", uri.getPort() == -1 ? uri.getHost() : uri.getHost() + ":" + port));
        headers.forEach((name, value) -> headerList.add(new Header(name, value)));
        return new ImmutableHttpRequest("POST", target, headerList, body, new Service(uri.getHost(), port, secure))
                .proxy();
    }

    private HttpRequest proxy() {
        return (HttpRequest) Proxy.newProxyInstance(HttpRequest.class.getClassLoader(),
                new Class<?>[] {HttpRequest.class}, this);
    }

    @Override
    public Object invoke(Object self, Method invoked, Object[] args) {
        return switch (invoked.getName()) {
            case "method" -> method;
            case "path" -> target;
            case "pathWithoutQuery" -> pathWithoutQuery();
            case "query" -> query();
            case "url" -> origin() + target;
            case "httpService" -> service;
            case "headers" -> headers;
            case "headerValue" -> headerValue((String) args[0]);
            case "hasHeader" -> headerValue((String) args[0]) != null;
            case "bodyToString" -> body;
            case "body" -> ByteArray.byteArray(body.getBytes(StandardCharsets.UTF_8));
            case "withPath" -> copy(method, (String) args[0], headers, body);
            case "withMethod" -> copy((String) args[0], target, headers, body);
            case "withBody" -> copy(method, target, headers, (String) args[0]);
            case "withService" -> new ImmutableHttpRequest(method, target, headers, body, (HttpService) args[0]).proxy();
            case "withHeader", "withUpdatedHeader" ->
                    copy(method, target, replaced((String) args[0], (String) args[1]), body);
            case "withAddedHeader" -> copy(method, target, added((String) args[0], (String) args[1]), body);
            case "withRemovedHeader" -> copy(method, target, removed(List.of((String) args[0])), body);
            case "withRemovedHeaders" -> copy(method, target, removed(namesOf(args[0])), body);
            case "toString" -> method + " " + target + " " + headers + " " + body;
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self == args[0];
            default -> throw new UnsupportedOperationException(invoked.getName());
        };
    }

    private HttpRequest copy(String newMethod, String newTarget, List<HttpHeader> newHeaders, String newBody) {
        return new ImmutableHttpRequest(newMethod, newTarget, newHeaders, newBody, service).proxy();
    }

    private String origin() {
        return (service.secure() ? "https://" : "http://") + service.host() + ":" + service.port();
    }

    private String pathWithoutQuery() {
        int query = target.indexOf('?');
        return query < 0 ? target : target.substring(0, query);
    }

    private String query() {
        int query = target.indexOf('?');
        return query < 0 ? "" : target.substring(query + 1);
    }

    private String headerValue(String name) {
        return headers.stream().filter(header -> header.name().equalsIgnoreCase(name))
                .map(HttpHeader::value).findFirst().orElse(null);
    }

    private List<HttpHeader> replaced(String name, String value) {
        if (headerValue(name) == null) {
            return added(name, value);
        }
        List<HttpHeader> result = new ArrayList<>();
        for (HttpHeader header : headers) {
            result.add(header.name().equalsIgnoreCase(name) ? new Header(header.name(), value) : header);
        }
        return result;
    }

    private List<HttpHeader> added(String name, String value) {
        List<HttpHeader> result = new ArrayList<>(headers);
        result.add(new Header(name, value));
        return result;
    }

    private List<HttpHeader> removed(List<String> names) {
        return headers.stream()
                .filter(header -> names.stream().noneMatch(name -> name.equalsIgnoreCase(header.name())))
                .toList();
    }

    private static List<String> namesOf(Object headerList) {
        return ((List<?>) headerList).stream().map(header -> ((HttpHeader) header).name()).toList();
    }
}
