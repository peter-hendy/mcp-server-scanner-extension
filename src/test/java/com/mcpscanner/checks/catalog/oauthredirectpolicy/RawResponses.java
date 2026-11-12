package com.mcpscanner.checks.catalog.oauthredirectpolicy;

final class RawResponses {

    private RawResponses() {
    }

    static String status(int status) {
        return statusLine(status) + "\r\n\r\n";
    }

    static String json(int status, String body) {
        return statusLine(status) + "\r\nContent-Type: application/json\r\n\r\n" + body;
    }

    static String json(String body) {
        return json(200, body);
    }

    static String redirect(String location) {
        return withHeader(302, "Location", location);
    }

    static String withHeader(int status, String name, String value) {
        return statusLine(status) + "\r\n" + name + ": " + value + "\r\n\r\n";
    }

    private static String statusLine(int status) {
        return "HTTP/1.1 " + status + " Scripted";
    }
}
