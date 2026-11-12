package com.mcpscanner.http;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.testutil.BurpBrowserTemplate;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BareRequestsTest {

    private static final String URL = "http://127.0.0.1:18800/mcp";

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void dropsEveryBrowserDefaultBurpStartsAUrlRequestWithButHost() {
        BurpBrowserTemplate.installFor(URL);

        HttpRequest request = BareRequests.fromUrl(URL);

        assertThat(request.headers()).extracting(header -> header.name() + ": " + header.value())
                .containsExactly("Host: 127.0.0.1:18800");
        assertThat(request.url()).isEqualTo(URL);
    }

    @Test
    void keepsATemplateCarryingOnlyHostAsItIs() {
        HttpRequest template = HttpRequest.httpRequestFromUrl(URL);

        HttpRequest request = BareRequests.fromUrl(URL);

        assertThat(request.headers()).extracting(HttpHeader::name)
                .containsExactlyElementsOf(template.headers().stream().map(HttpHeader::name).toList());
        assertThat(request.url()).isEqualTo(URL);
    }
}
