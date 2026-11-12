package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import com.mcpscanner.checks.shared.html.HtmlDocument;

import java.util.List;
import java.util.Optional;

record FieldMarkers(StructureMarker clientName, StructureMarker state) {

    static FieldMarkers from(StructureMarkers markers) {
        return new FieldMarkers(markers.next(), markers.next());
    }

    List<FieldReflection> reflectionsIn(HtmlDocument document) {
        return List.of(reflection(ReflectedField.CLIENT_NAME, clientName, document),
                        reflection(ReflectedField.STATE, state, document))
                .stream()
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<FieldReflection> reflection(ReflectedField field, StructureMarker marker,
                                                        HtmlDocument document) {
        return marker.placementIn(document).map(placement -> new FieldReflection(field, marker, placement));
    }
}
