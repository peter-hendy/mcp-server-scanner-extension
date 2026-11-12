package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import com.mcpscanner.checks.shared.html.HtmlDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldMarkersTest {

    private final FieldMarkers markers = new FieldMarkers(
            StructureMarker.withNonce("aaaa"), StructureMarker.withNonce("bbbb"));

    @Test
    void reportsEachFieldWhoseMarkerParsedAsStructure() {
        HtmlDocument page = HtmlDocument.parse("<h1>" + markers.clientName().payload() + "</h1>"
                + "<input value=\"" + markers.state().payload().replace("<", "&lt;").replace(">", "&gt;") + "\">");

        assertThat(markers.reflectionsIn(page)).containsExactly(
                new FieldReflection(ReflectedField.CLIENT_NAME, markers.clientName(), StructurePlacement.ELEMENT),
                new FieldReflection(ReflectedField.STATE, markers.state(), StructurePlacement.ATTRIBUTE));
    }

    @Test
    void escapedPageHasNoReflections() {
        assertThat(markers.reflectionsIn(HtmlDocument.parse("<p>&lt;mcpscan-el-aaaa&gt;</p>"))).isEmpty();
    }

    @Test
    void generatedFieldMarkersAreDistinct() {
        FieldMarkers generated = FieldMarkers.from(new StructureMarkers());

        assertThat(generated.clientName()).isNotEqualTo(generated.state());
    }
}
