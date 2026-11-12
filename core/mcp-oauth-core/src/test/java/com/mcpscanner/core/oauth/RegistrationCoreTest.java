package com.mcpscanner.core.oauth;

import java.net.URI;
import java.util.Map;
import java.util.List;

public final class RegistrationCoreTest {
    private static final URI ISSUER = URI.create("https://issuer.example");
    private static final URI CALLBACK = URI.create("http://127.0.0.1:8765/callback");
    private static final URI CIMD = URI.create("https://client.example/oauth/client.json");
    private static final URI DCR = URI.create("https://issuer.example/register");

    public static void main(String[] args) {
        selectsRegisteredCredentialsFirst();
        selectsCimdBeforeDcr();
        selectsDcrWhenCimdUnsupported();
        respectsExplicitMode();
        validatesRegisteredClient();
        validatesCimdLocation();
        validatesCimdDocument();
        System.out.println("RegistrationCoreTest: 7 behavior groups passed");
    }

    private static void selectsRegisteredCredentialsFirst() {
        var request = new RegistrationRequest(ISSUER, new RegisteredClient(ISSUER, "registered", "secret"), CIMD,
                new AuthorizationServerCapabilities(true, DCR), CALLBACK, RegistrationMode.AUTO);
        var result = new RegistrationSelector().select(request);
        require(result.mode() == RegistrationMode.PRE_REGISTERED, "Pre-registered credentials take precedence");
        require(result.clientId().equals("registered") && result.clientSecret().equals("secret"), "Credentials retained");
        require(!request.toString().contains("secret"), "Registration request must not expose client secret");
        require(!result.toString().contains("secret"), "Registration selection must not expose client secret");
    }

    private static void selectsCimdBeforeDcr() {
        var cimd = new RegistrationSelector().select(new RegistrationRequest(ISSUER, null, CIMD,
                new AuthorizationServerCapabilities(true, DCR), CALLBACK, RegistrationMode.AUTO));
        require(cimd.mode() == RegistrationMode.CIMD && cimd.clientId().equals(CIMD.toString()), "CIMD precedes DCR");
    }

    private static void selectsDcrWhenCimdUnsupported() {
        var dcr = new RegistrationSelector().select(new RegistrationRequest(ISSUER, null, CIMD,
                new AuthorizationServerCapabilities(false, DCR), CALLBACK, RegistrationMode.AUTO));
        require(dcr.mode() == RegistrationMode.DCR && DCR.equals(dcr.registrationEndpoint()), "Unsupported CIMD falls through to DCR");
        expect(RegistrationException.Reason.MISSING_REGISTRATION, () -> new RegistrationSelector().select(
                new RegistrationRequest(ISSUER, null, null, new AuthorizationServerCapabilities(false, null), CALLBACK, RegistrationMode.AUTO)));
    }

    private static void respectsExplicitMode() {
        for (var mode : new RegistrationMode[]{RegistrationMode.PRE_REGISTERED, RegistrationMode.CIMD, RegistrationMode.DCR}) {
            expect(RegistrationException.Reason.UNSUPPORTED_MODE, () -> new RegistrationSelector().select(
                    new RegistrationRequest(ISSUER, null, CIMD, new AuthorizationServerCapabilities(false, null), CALLBACK, mode)));
            var selected = new RegistrationSelector().select(new RegistrationRequest(ISSUER,
                    new RegisteredClient(ISSUER, "registered", "secret"), CIMD,
                    new AuthorizationServerCapabilities(true, DCR), CALLBACK, mode));
            require(selected.mode() == mode, "Explicit " + mode + " takes precedence over Auto order");
        }
    }

    private static void validatesRegisteredClient() {
        expect(RegistrationException.Reason.ISSUER_MISMATCH, () -> new RegistrationSelector().select(
                new RegistrationRequest(ISSUER, new RegisteredClient(URI.create("https://other.example"), "client", null),
                        CIMD, new AuthorizationServerCapabilities(true, DCR), CALLBACK, RegistrationMode.AUTO)));
        expect(RegistrationException.Reason.INVALID_CLIENT, () -> new RegistrationSelector().select(
                new RegistrationRequest(ISSUER, new RegisteredClient(ISSUER, "  ", null),
                        CIMD, new AuthorizationServerCapabilities(true, DCR), CALLBACK, RegistrationMode.AUTO)));
    }

    private static void validatesCimdLocation() {
        for (String url : new String[]{"http://client.example/client.json", "https://client.example", "https://user@client.example/client.json", "https://client.example/client.json#fragment"}) {
            expect(RegistrationException.Reason.INVALID_CIMD_URL, () -> new RegistrationSelector().select(
                    new RegistrationRequest(ISSUER, null, URI.create(url), new AuthorizationServerCapabilities(true, DCR), CALLBACK, RegistrationMode.CIMD)));
        }
        for (String callback : new String[]{"http://127.0.0.1/callback", "http://127.0.0.1:0/callback", "http://localhost:8765/callback"}) {
            expect(RegistrationException.Reason.INVALID_CALLBACK, () -> new RegistrationSelector().select(
                    new RegistrationRequest(ISSUER, null, CIMD, new AuthorizationServerCapabilities(true, DCR), URI.create(callback), RegistrationMode.CIMD)));
        }
    }

    private static void validatesCimdDocument() {
        var document = Map.<String, Object>of("client_id", CIMD.toString(), "client_name", "MCP scanner",
                "redirect_uris", List.of(CALLBACK.toString()));
        new CimdDocumentValidator().validate(CIMD, document, CALLBACK);
        for (Object clientId : new Object[]{"https://client.example/oauth/other.json", 42, ""}) {
            var invalid = new java.util.HashMap<>(document);
            invalid.put("client_id", clientId);
            expect(RegistrationException.Reason.INVALID_CIMD_DOCUMENT, () -> new CimdDocumentValidator().validate(CIMD, invalid, CALLBACK));
        }
        for (Object name : new Object[]{" ", 42}) {
            var invalid = new java.util.HashMap<>(document);
            invalid.put("client_name", name);
            expect(RegistrationException.Reason.INVALID_CIMD_DOCUMENT, () -> new CimdDocumentValidator().validate(CIMD, invalid, CALLBACK));
        }
        var noName = new java.util.HashMap<>(document);
        noName.remove("client_name");
        expect(RegistrationException.Reason.INVALID_CIMD_DOCUMENT, () -> new CimdDocumentValidator().validate(CIMD, noName, CALLBACK));
        for (Object redirects : new Object[]{List.of("http://127.0.0.1:8766/callback"), List.of("http://localhost:8765/callback"), CALLBACK.toString(), List.of(42)}) {
            var invalid = new java.util.HashMap<>(document);
            invalid.put("redirect_uris", redirects);
            expect(RegistrationException.Reason.INVALID_CIMD_DOCUMENT, () -> new CimdDocumentValidator().validate(CIMD, invalid, CALLBACK));
        }
        var publicClient = new java.util.HashMap<>(document);
        publicClient.put("token_endpoint_auth_method", "none");
        new CimdDocumentValidator().validate(CIMD, publicClient, CALLBACK);
        for (Object method : new Object[]{"client_secret_basic", "", 42}) {
            var invalid = new java.util.HashMap<>(document);
            invalid.put("token_endpoint_auth_method", method);
            expect(RegistrationException.Reason.INVALID_CIMD_DOCUMENT, () -> new CimdDocumentValidator().validate(CIMD, invalid, CALLBACK));
        }

    }

    private static void expect(RegistrationException.Reason reason, Runnable action) {
        try { action.run(); } catch (RegistrationException e) {
            require(e.reason() == reason, "Expected " + reason + " but got " + e.reason());
            return;
        }
        throw new AssertionError("Expected " + reason);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
