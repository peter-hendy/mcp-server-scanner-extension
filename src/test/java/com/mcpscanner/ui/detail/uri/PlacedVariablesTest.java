package com.mcpscanner.ui.detail.uri;

import com.mcpscanner.mcp.uritemplate.UriTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlacedVariablesTest {

    @Test
    void placesTheMockTemplatesVariables() {
        assertThat(placed("repo://{owner}/{repo}/issues{?state,labels*}")).containsExactly(
                new PlacedVariable("owner", "path segment 1", "simple"),
                new PlacedVariable("repo", "path segment 2", "simple"),
                new PlacedVariable("state", "query", "form query (?)"),
                new PlacedVariable("labels", "query", "form query (?), exploded (*)"));
    }

    @Test
    void placesEveryOperatorOnAWebUri() {
        assertThat(placed("https://{host}:{port}/v1/{id}{+rest}{/seg}{;param}{.format}{?q}{&page}{#section}"))
                .containsExactly(
                        new PlacedVariable("host", "host", "simple"),
                        new PlacedVariable("port", "port", "simple"),
                        new PlacedVariable("id", "path segment 2", "simple"),
                        new PlacedVariable("rest", "path segment 2", "reserved (+)"),
                        new PlacedVariable("seg", "path segment 3", "path (/)"),
                        new PlacedVariable("param", "path parameter", "path-style (;)"),
                        new PlacedVariable("format", "path segment 3", "label (.)"),
                        new PlacedVariable("q", "query", "form query (?)"),
                        new PlacedVariable("page", "query", "query continuation (&)"),
                        new PlacedVariable("section", "fragment", "fragment (#)"));
    }

    @Test
    void aLabelInTheHostIsPartOfTheHost() {
        assertThat(placed("https://api{.region}.example.com/"))
                .containsExactly(new PlacedVariable("region", "host", "label (.)"));
    }

    @Test
    void anEmptyAuthorityStartsThePathAtSegmentOne() {
        assertThat(placed("file:///{dir}/{name:8}")).containsExactly(
                new PlacedVariable("dir", "path segment 1", "simple"),
                new PlacedVariable("name", "path segment 2", "simple, prefix (:8)"));
    }

    @Test
    void eachPathOperatorVariableTakesItsOwnSegment() {
        assertThat(placed("docs://root{/a,b}")).containsExactly(
                new PlacedVariable("a", "path segment 2", "path (/)"),
                new PlacedVariable("b", "path segment 3", "path (/)"));
    }

    @Test
    void literalQueryAndFragmentDelimitersMoveTheContext() {
        assertThat(placed("search?term={term}#{anchor}")).containsExactly(
                new PlacedVariable("term", "query", "simple"),
                new PlacedVariable("anchor", "fragment", "simple"));
    }

    @Test
    void anOpaqueUriPlacesVariablesInItsPath() {
        assertThat(placed("urn:isbn:{isbn}")).containsExactly(new PlacedVariable("isbn", "path segment 1", "simple"));
    }

    private static List<PlacedVariable> placed(String text) {
        return PlacedVariables.of(UriTemplate.parse(text).orElseThrow());
    }
}
