package com.mcpscanner.checks.shared.oauth;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static com.mcpscanner.checks.shared.oauth.ScriptedHttp.json;
import static org.assertj.core.api.Assertions.assertThat;

class JsonDocumentTest {

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    private static HttpRequestResponse exchange(String rawResponse) {
        return HttpRequestResponse.httpRequestResponse(HttpRequest.httpRequestFromUrl("https://as.example.com/"),
                HttpResponse.httpResponse(rawResponse));
    }

    private static JsonDocument document(String json) {
        return JsonDocument.parse(json).orElseThrow();
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 201, 299})
    void successfulResponseWithAnObjectIsParsed(int status) {
        assertThat(JsonDocument.fromSuccessfulResponse(exchange(json(status, "{\"a\":\"b\"}")))).isPresent();
    }

    @ParameterizedTest
    @ValueSource(ints = {199, 300, 302, 404, 500})
    void unsuccessfulResponseIsIgnored(int status) {
        assertThat(JsonDocument.fromSuccessfulResponse(exchange(json(status, "{\"a\":\"b\"}")))).isEmpty();
    }

    @Test
    void nonObjectJsonIsIgnored() {
        assertThat(JsonDocument.parse("[1,2]")).isEmpty();
    }

    @Test
    void malformedJsonIsIgnored() {
        assertThat(JsonDocument.parse("{not json")).isEmpty();
        assertThat(JsonDocument.parse("<html>")).isEmpty();
    }

    @Test
    void emptyBodyIsIgnored() {
        assertThat(JsonDocument.parse("")).isEmpty();
    }

    @Test
    void textReturnsANonBlankString() {
        assertThat(document("{\"a\":\"b\"}").text("a")).contains("b");
    }

    @Test
    void textIgnoresBlankMissingAndNonTextualValues() {
        JsonDocument document = document("{\"blank\":\" \",\"number\":1}");

        assertThat(document.text("blank")).isEmpty();
        assertThat(document.text("number")).isEmpty();
        assertThat(document.text("missing")).isEmpty();
    }

    @Test
    void textsKeepsOnlyTextualArrayElements() {
        assertThat(document("{\"a\":[\"x\",1,null,\"y\"]}").texts("a")).containsExactly("x", "y");
    }

    @Test
    void objectsKeepsOnlyObjectArrayElements() {
        assertThat(document("{\"keys\":[{\"kid\":\"k1\"},\"x\",7,[],{\"kid\":\"k2\"}]}").objects("keys"))
                .extracting(key -> key.path("kid").asText())
                .containsExactly("k1", "k2");
    }

    @Test
    void objectsIsEmptyForMissingOrNonArrayValues() {
        assertThat(document("{\"keys\":{\"kid\":\"k1\"}}").objects("keys")).isEmpty();
        assertThat(document("{}").objects("keys")).isEmpty();
    }

    @Test
    void textsIsEmptyForMissingOrNonArrayValues() {
        JsonDocument document = document("{\"a\":\"x\"}");

        assertThat(document.texts("a")).isEmpty();
        assertThat(document.texts("missing")).isEmpty();
    }

    @Test
    void webUriAcceptsAnHttpsUrl() {
        assertThat(document("{\"u\":\"https://as.example.com/authorize\"}").webUri("u"))
                .contains(URI.create("https://as.example.com/authorize"));
    }

    @Test
    void webUriRejectsANonWebUrl() {
        assertThat(document("{\"u\":\"javascript:alert(1)\"}").webUri("u")).isEmpty();
    }
}
