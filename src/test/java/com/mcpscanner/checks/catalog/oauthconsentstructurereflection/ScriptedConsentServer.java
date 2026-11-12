package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.checks.shared.oauth.ScriptedHttp;
import com.mcpscanner.mcp.McpObjectMapper;

import java.util.function.UnaryOperator;

import static com.mcpscanner.checks.shared.oauth.ScriptedHttp.html;
import static com.mcpscanner.checks.shared.oauth.ScriptedHttp.json;
import static com.mcpscanner.checks.shared.oauth.ScriptedHttp.queryParameter;

final class ScriptedConsentServer {

    static final String ORIGIN = "https://mcp.example";
    static final String MCP = ORIGIN + "/mcp";
    static final String REGISTER = ORIGIN + "/register";
    static final String AUTHORIZE = ORIGIN + "/authorize";
    static final String CLIENT_ID = "client-1";
    static final String MANAGED_CLIENT = REGISTER + "/" + CLIENT_ID;

    static final UnaryOperator<String> RAW = UnaryOperator.identity();
    static final UnaryOperator<String> ESCAPED = value -> value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    static final UnaryOperator<String> ANGLES_ESCAPED = value -> value.replace("<", "&lt;").replace(">", "&gt;");

    private UnaryOperator<String> clientNameEncoding = ESCAPED;
    private UnaryOperator<String> stateEncoding = ESCAPED;
    private boolean controlEchoesStateRaw;
    private boolean controlAnswers = true;
    private boolean controlAnswersWithJson;
    private boolean consentBehindRedirectEchoingState;
    private boolean registrationAdvertised = true;
    private boolean registrationAccepted = true;
    private boolean managesClients;
    private boolean throttlesAuthorization;
    private String[] consentHeaders = {};
    private String registeredName = "";

    ScriptedConsentServer clientName(UnaryOperator<String> encoding) {
        this.clientNameEncoding = encoding;
        return this;
    }

    ScriptedConsentServer state(UnaryOperator<String> encoding) {
        this.stateEncoding = encoding;
        return this;
    }

    ScriptedConsentServer controlEchoesStateRaw() {
        this.controlEchoesStateRaw = true;
        return this;
    }

    ScriptedConsentServer controlAnswersWithJson() {
        this.controlAnswersWithJson = true;
        return this;
    }

    ScriptedConsentServer consentBehindRedirectEchoingState() {
        this.consentBehindRedirectEchoingState = true;
        return this;
    }

    ScriptedConsentServer controlUnanswered() {
        this.controlAnswers = false;
        return this;
    }

    ScriptedConsentServer withoutRegistration() {
        this.registrationAdvertised = false;
        return this;
    }

    ScriptedConsentServer rejectingRegistration() {
        this.registrationAccepted = false;
        return this;
    }

    ScriptedConsentServer managingClients() {
        this.managesClients = true;
        return this;
    }

    ScriptedConsentServer throttlingAuthorization() {
        this.throttlesAuthorization = true;
        return this;
    }

    ScriptedConsentServer consentHeaders(String... headers) {
        this.consentHeaders = headers;
        return this;
    }

    ScriptedHttp build() {
        return new ScriptedHttp()
                .on("GET", ORIGIN + "/.well-known/oauth-authorization-server", json(200,
                        "{\"authorization_endpoint\":\"" + AUTHORIZE + "\""
                                + (registrationAdvertised ? ",\"registration_endpoint\":\"" + REGISTER + "\"" : "")
                                + "}"))
                .on("POST", REGISTER, this::register)
                .on("DELETE", MANAGED_CLIENT, request -> ScriptedHttp.status(204))
                .on("GET", AUTHORIZE, this::authorize)
                .on("GET", ORIGIN + "/consent", this::consent);
    }

    static HttpRequestResponse mcpBase() {
        return HttpRequestResponse.httpRequestResponse(
                HttpRequest.httpRequestFromUrl(MCP).withMethod("POST")
                        .withBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"),
                HttpResponse.httpResponse("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n"
                        + "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}"));
    }

    private String register(HttpRequest request) {
        if (!registrationAccepted) {
            return json(400, "{\"error\":\"invalid_client_metadata\"}");
        }
        try {
            registeredName = McpObjectMapper.INSTANCE.readTree(request.bodyToString()).path("client_name").asText();
        } catch (Exception malformed) {
            return json(400, "{}");
        }
        return json(201, "{\"client_id\":\"" + CLIENT_ID + "\"" + (managesClients
                ? ",\"registration_client_uri\":\"" + MANAGED_CLIENT + "\",\"registration_access_token\":\"rat\""
                : "") + "}");
    }

    private String authorize(HttpRequest request) {
        if (throttlesAuthorization) {
            return ScriptedHttp.status(429);
        }
        String state = queryParameter(request, "state");
        if (!CLIENT_ID.equals(queryParameter(request, "client_id"))) {
            if (!controlAnswers) {
                return ScriptedHttp.NO_RESPONSE;
            }
            if (controlAnswersWithJson) {
                return json(400, "{\"error\":\"invalid_client\",\"state\":\"" + state.replace("\"", "\\\"") + "\"}");
            }
            return html(400, "<h1>invalid_client</h1>" + (controlEchoesStateRaw ? "<p>" + state + "</p>" : ""));
        }
        if (consentBehindRedirectEchoingState) {
            return html(302, "<p>Redirecting " + state + "</p>", "Location: /consent?tx=1");
        }
        return consentPage(state);
    }

    private String consent(HttpRequest request) {
        return consentPage("escaped-state");
    }

    private String consentPage(String state) {
        return html("<!doctype html><title>Authorize</title><h1>Authorize "
                + clientNameEncoding.apply(registeredName) + "</h1><form method=\"post\">"
                + "<input type=\"hidden\" name=\"state\" value=\"" + stateEncoding.apply(state) + "\">"
                + "<button>Approve</button></form>", consentHeaders);
    }
}
