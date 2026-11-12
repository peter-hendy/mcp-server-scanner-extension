package com.mcpscanner.checks.catalog.downstreamrouteescape;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteProbeCatalogTest {

    private final List<RouteProbe> probes = RouteProbeCatalog.standard();

    @Test
    void dotSlashProbesClimbOneToFiveLevelsBeforeTheirSuffixedRetry() {
        assertThat(probes.get(0)).isEqualTo(new RouteProbe(List.of("../", ".././"),
                List.of(".../", "..././", ".../mcp/../"), List.of()));
        assertThat(probes.get(1)).isEqualTo(new RouteProbe(
                List.of("../?mcp_route=1", ".././?mcp_route=1"),
                List.of(".../?mcp_route=1", "..././?mcp_route=1", ".../mcp/../?mcp_route=1"), List.of()));
        assertThat(probes.get(8).traversals()).containsExactly("../../../../../", "../../../../.././");
        assertThat(probes.get(9).controls()).containsExactly(".../.../.../.../.../?mcp_route=1",
                ".../.../.../.../..././?mcp_route=1", ".../.../.../.../.../mcp/../?mcp_route=1");
    }

    @Test
    void everyDotSlashProbeHasADetourControlThatResolvesBackToThePlainControl() {
        assertThat(probes.subList(0, 10)).allSatisfy(probe ->
                assertThat(probe.controls().get(2)).contains("mcp/../"));
    }

    @Test
    void bareParentSegmentComesLastAndMustDifferFromTheCurrentSegment() {
        assertThat(probes).hasSize(11);
        assertThat(probes.get(10)).isEqualTo(new RouteProbe(List.of("..", ".."), List.of("...", "...."),
                List.of(".")));
    }

    @Test
    void everyProbeHasAtLeastTwoTraversalsAndTwoControls() {
        assertThat(probes).allSatisfy(probe -> {
            assertThat(probe.traversals()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(probe.controls()).hasSizeGreaterThanOrEqualTo(2);
        });
    }
}
