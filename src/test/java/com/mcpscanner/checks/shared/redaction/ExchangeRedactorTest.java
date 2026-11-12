package com.mcpscanner.checks.shared.redaction;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExchangeRedactorTest {

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void redactsSecretsInTheRequestBodyAuthorizationHeaderAndResponse() {
        ExchangeRedactor redactor = new ExchangeRedactor(Set.of("refresh-1", "Y3JlZHM=", "access-9"));
        HttpRequest request = HttpRequest.httpRequestFromUrl("https://as.example/token").withMethod("POST")
                .withHeader("Authorization", "Basic Y3JlZHM=").withBody("refresh_token=refresh-1");
        HttpResponse response = HttpResponse.httpResponse(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 99\r\n\r\n{\"access_token\":\"access-9\"}");

        HttpRequestResponse redacted = redactor.redact(HttpRequestResponse.httpRequestResponse(request, response));

        assertThat(redacted.request().bodyToString()).isEqualTo("refresh_token=" + ExchangeRedactor.REDACTED);
        assertThat(redacted.request().headerValue("Authorization")).isEqualTo("Basic " + ExchangeRedactor.REDACTED);
        assertThat(redacted.response().statusCode()).isEqualTo((short) 200);
        assertThat(redacted.response().bodyToString()).doesNotContain("access-9");
        assertThat(redacted.response().headerValue("Content-Length")).isNull();
        assertThat(redacted.response().headerValue("Content-Type")).isEqualTo("application/json");
    }

    @Test
    void redactsTheLongestSecretFirstAndIgnoresTrivialValues() {
        ExchangeRedactor redactor = new ExchangeRedactor(Set.of("abcd", "abcdef", "ab"));

        assertThat(redactor.redact("abcdef ab")).isEqualTo(ExchangeRedactor.REDACTED + " ab");
    }

    @Test
    void redactsUrlEncodedAndJsonEscapedFormsOfASecret() {
        ExchangeRedactor redactor = new ExchangeRedactor(Set.of("ab+c/d=="));

        assertThat(redactor.redact("refresh_token=ab%2Bc%2Fd%3D%3D")).isEqualTo("refresh_token=" + ExchangeRedactor.REDACTED);
        assertThat(redactor.redact("x ab+c\\/d== y")).isEqualTo("x " + ExchangeRedactor.REDACTED + " y");
    }

    @Test
    void redactsTokenFieldsItWasNeverTold() {
        ExchangeRedactor redactor = new ExchangeRedactor(Set.of());

        assertThat(redactor.redact("{\"id_token\":\"eyJ.x.y\",\"access_token\" : \"abc\",\"scope\":\"read\"}"))
                .isEqualTo("{\"id_token\":\"" + ExchangeRedactor.REDACTED + "\",\"access_token\" : \""
                        + ExchangeRedactor.REDACTED + "\",\"scope\":\"read\"}");
    }

    @Test
    void keepsAMissingResponseMissing() {
        HttpRequestResponse redacted = new ExchangeRedactor(Set.of()).redact(HttpRequestResponse.httpRequestResponse(
                HttpRequest.httpRequestFromUrl("https://as.example/token"), null));

        assertThat(redacted.response()).isNull();
    }

    @Test
    void replacesTheResponseBodyKeepingStatusAndHeaders() {
        HttpResponse response = HttpResponse.httpResponse(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 9\r\n\r\n{\"k\":\"v\"}");

        HttpRequestResponse redacted = new ExchangeRedactor(Set.of("abcdef")).redact(
                HttpRequestResponse.httpRequestResponse(HttpRequest.httpRequestFromUrl("https://h.example/f.json"),
                        response), "{\"masked\":\"abcdef\"}");

        assertThat(redacted.response().statusCode()).isEqualTo((short) 200);
        assertThat(redacted.response().headerValue("Content-Type")).isEqualTo("application/json");
        assertThat(redacted.response().bodyToString())
                .isEqualTo("{\"masked\":\"" + ExchangeRedactor.REDACTED + "\"}");
    }
}
