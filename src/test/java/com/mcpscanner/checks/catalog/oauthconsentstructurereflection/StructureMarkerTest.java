package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import com.mcpscanner.checks.shared.html.HtmlDocument;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class StructureMarkerTest {

    private final StructureMarker marker = StructureMarker.withNonce("a1b2c3");

    @Test
    void payloadCarriesOnlyInertMarkupNamedByTheNonce() {
        assertThat(marker.payload())
                .isEqualTo("mcpscan-a1b2c3\"' mcpscan-at-a1b2c3=\"1\"></script>"
                        + "<mcpscan-el-a1b2c3></mcpscan-el-a1b2c3>")
                .doesNotContainIgnoringCase("on")
                .doesNotContainIgnoringCase("javascript:");
    }

    @Test
    void rawTextReflectionParsesAsAnElement() {
        assertThat(marker.placementIn(page("<h1>Authorize " + marker.payload() + "</h1>")))
                .contains(StructurePlacement.ELEMENT);
    }

    @Test
    void rawDoubleQuotedAttributeReflectionParsesAsAnElement() {
        assertThat(marker.placementIn(page("<input value=\"" + marker.payload() + "\">")))
                .contains(StructurePlacement.ELEMENT);
    }

    @Test
    void rawScriptStringReflectionParsesAsAnElementOnlyThroughTheScriptBreakout() {
        assertThat(marker.placementIn(page("<script>var c = \"" + marker.payload() + "\";</script>")))
                .contains(StructurePlacement.ELEMENT);
    }

    @Test
    void attributeReflectionWithAngleBracketsEncodedParsesAsAnAttribute() {
        String angleEncoded = marker.payload().replace("<", "&lt;").replace(">", "&gt;");

        assertThat(marker.placementIn(page("<input value=\"" + angleEncoded + "\">")))
                .contains(StructurePlacement.ATTRIBUTE);
    }

    @Test
    void singleQuotedAndUnquotedAttributeReflectionsAlsoParseAsAttributesWhenAnglesAreEncoded() {
        String angleEncoded = marker.payload().replace("<", "&lt;").replace(">", "&gt;");

        assertThat(marker.placementIn(page("<input value='" + angleEncoded + "'>")))
                .contains(StructurePlacement.ATTRIBUTE);
        assertThat(marker.placementIn(page("<input value=" + angleEncoded + ">")))
                .contains(StructurePlacement.ATTRIBUTE);
    }

    @Test
    void fullyEscapedReflectionHasNoPlacement() {
        String escaped = marker.payload().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");

        assertThat(marker.placementIn(page("<h1>" + escaped + "</h1><input value=\"" + escaped + "\">")))
                .isEmpty();
    }

    @Test
    void jsonEscapedScriptReflectionHasNoPlacement() {
        String jsonEscaped = marker.payload().replace("\"", "\\\"").replace("<", "\\u003c").replace(">", "\\u003e");

        assertThat(marker.placementIn(page("<script>var c = \"" + jsonEscaped + "\";</script>"))).isEmpty();
    }

    @Test
    void anotherMarkersStructureIsNotThisMarkersPlacement() {
        StructureMarker other = StructureMarker.withNonce("ffffff");

        assertThat(marker.placementIn(page("<h1>" + other.payload() + "</h1>"))).isEmpty();
    }

    @Test
    void generatedMarkersAreDistinctAndLowercaseAlphanumeric() {
        StructureMarkers markers = new StructureMarkers();

        assertThat(IntStream.range(0, 20).mapToObj(i -> markers.next().elementName()).distinct())
                .hasSize(20)
                .allMatch(name -> name.matches("mcpscan-el-[0-9a-f]{16}"));
    }

    private static HtmlDocument page(String body) {
        return HtmlDocument.parse("<!doctype html><html><body>" + body + "</body></html>");
    }
}
