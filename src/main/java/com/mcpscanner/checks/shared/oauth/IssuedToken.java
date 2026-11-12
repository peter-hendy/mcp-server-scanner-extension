package com.mcpscanner.checks.shared.oauth;

public record IssuedToken(String value, String typeHint) {

    public static IssuedToken access(String value) {
        return new IssuedToken(value, "access_token");
    }

    public static IssuedToken refresh(String value) {
        return new IssuedToken(value, "refresh_token");
    }
}
