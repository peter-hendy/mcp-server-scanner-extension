package com.mcpscanner.checks.shared.html;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlDocumentTest {

    private static final String ELEMENT = "mk-el-abc123";
    private static final String ATTRIBUTE = "mk-attr-abc123";

    @Test
    void elementInBodyTextIsActive() {
        assertThat(HtmlDocument.parse("<h1>Authorize x<mk-el-abc123></mk-el-abc123></h1>")
                .containsActiveElement(ELEMENT)).isTrue();
    }

    @Test
    void elementMatchingIgnoresTagNameCase() {
        assertThat(HtmlDocument.parse("<p><MK-EL-ABC123></p>").containsActiveElement(ELEMENT)).isTrue();
    }

    @Test
    void elementBreakingOutOfAScriptIsActive() {
        assertThat(HtmlDocument.parse("<script>var n = \"x</script><mk-el-abc123></mk-el-abc123>\";</script>")
                .containsActiveElement(ELEMENT)).isTrue();
    }

    @Test
    void elementBreakingOutOfADoubleQuotedAttributeIsActive() {
        assertThat(HtmlDocument.parse("<input value=\"x\"><mk-el-abc123></mk-el-abc123>\">")
                .containsActiveElement(ELEMENT)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<p>&lt;mk-el-abc123&gt;</p>",
            "<input value=\"&lt;mk-el-abc123&gt;\">",
            "<input value=\"<mk-el-abc123>\">",
            "<script>var n = \"<mk-el-abc123>\";</script>",
            "<script type=\"application/json\">{\"n\":\"<mk-el-abc123>\"}</script>",
            "<style>/* <mk-el-abc123> */</style>",
            "<title><mk-el-abc123></title>",
            "<textarea><mk-el-abc123></textarea>",
            "<xmp><mk-el-abc123></xmp>",
            "<iframe><mk-el-abc123></iframe>",
            "<noembed><mk-el-abc123></noembed>",
            "<noframes><mk-el-abc123></noframes>",
            "<noscript><mk-el-abc123></noscript>",
            "<template><p><mk-el-abc123></p></template>",
            "<select><option><mk-el-abc123></option></select>",
            "<!-- <mk-el-abc123> -->",
            "<plaintext><mk-el-abc123>",
            "<p>mk-el-abc123</p>"
    })
    void markerOutsideAnActiveElementContextIsNotActive(String html) {
        assertThat(HtmlDocument.parse(html).containsActiveElement(ELEMENT)).isFalse();
    }

    @Test
    void injectedAttributeOnARealTagIsActive() {
        assertThat(HtmlDocument.parse("<input type=\"hidden\" value=\"x\" mk-attr-abc123=\"1\">")
                .containsActiveAttribute(ATTRIBUTE)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<p>x\" mk-attr-abc123=\"1\"</p>",
            "<input value=\"x&quot; mk-attr-abc123=&quot;1\">",
            "<script>var s = '\" mk-attr-abc123=\"1';</script>",
            "<template><input mk-attr-abc123=\"1\"></template>",
            "<noscript><input mk-attr-abc123=\"1\"></noscript>"
    })
    void attributeTextOrInertAttributeIsNotActive(String html) {
        assertThat(HtmlDocument.parse(html).containsActiveAttribute(ATTRIBUTE)).isFalse();
    }

    @Test
    void metaContentSecurityPoliciesAreReturnedCaseInsensitively() {
        HtmlDocument document = HtmlDocument.parse("<head>"
                + "<meta http-equiv=\"content-security-policy\" content=\"script-src 'self'\">"
                + "<meta http-equiv=\"Content-Security-Policy-Report-Only\" content=\"script-src 'none'\">"
                + "<meta name=\"viewport\" content=\"width=device-width\"></head>");

        assertThat(document.metaContentSecurityPolicies()).containsExactly("script-src 'self'");
    }

    @Test
    void metaPolicyOutsideTheHeadIsIgnoredAsBrowsersDo() {
        assertThat(HtmlDocument.parse("<head></head><body><p>x</p><meta http-equiv=\"Content-Security-Policy\" "
                + "content=\"script-src 'none'\"></body>").metaContentSecurityPolicies()).isEmpty();
    }

    @Test
    void metaPolicyInsideATemplateIsIgnored() {
        assertThat(HtmlDocument.parse("<template><meta http-equiv=\"Content-Security-Policy\" "
                + "content=\"script-src 'none'\"></template>").metaContentSecurityPolicies()).isEmpty();
    }
}
