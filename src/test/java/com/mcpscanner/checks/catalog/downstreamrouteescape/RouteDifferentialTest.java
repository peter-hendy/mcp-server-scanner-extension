package com.mcpscanner.checks.catalog.downstreamrouteescape;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RouteDifferentialTest {

    private static final RouteProbe PROBE = new RouteProbe(List.of("../", ".././"),
            List.of(".../", "..././", ".../mcp/../"), List.of());
    private static final RouteProbe BARE = new RouteProbe(List.of("..", ".."), List.of("...", "...."), List.of("."));

    private final RouteDifferential differential = new RouteDifferential(new ReplySignature(new PayloadMask()),
            new RejectionWording());

    @Test
    void stableTraversalResultThatDiffersFromStableControlsIsADifference() {
        ScriptedSender sender = new ScriptedSender(RouteDifferentialTest::router);

        Optional<RouteDifference> difference = differential.run(PROBE, sender);

        assertThat(difference).isPresent();
        assertThat(difference.get().probe()).isEqualTo(PROBE);
        assertThat(difference.get().evidence()).hasSize(7);
        assertThat(sender.sent).containsExactly("../", ".././", ".../", "..././", ".../mcp/../", "../", ".../");
    }

    @Test
    void traversalToolErrorStopsAfterTheFirstRequest() {
        ScriptedSender sender = new ScriptedSender(value -> Replies.toolError("invalid id"));

        assertThat(differential.run(PROBE, sender)).isEmpty();
        assertThat(sender.sent).containsExactly("../");
    }

    @Test
    void traversalSpellingsThatDisagreeAreNotADifference() {
        ScriptedSender sender = new ScriptedSender(RouteDifferentialTest::router)
                .then(".././", Replies.toolText("{\"other\":true}"));

        assertThat(differential.run(PROBE, sender)).isEmpty();
        assertThat(sender.sent).containsExactly("../", ".././");
    }

    @Test
    void controlsMatchingTheTraversalAreNotADifference() {
        ScriptedSender sender = new ScriptedSender(value -> Replies.toolText("{\"error\":\"not found\"}"));

        assertThat(differential.run(PROBE, sender)).isEmpty();
    }

    @Test
    void resultsThatDifferOnlyInATimingValueAreNotADifference() {
        ScriptedSender sender = new ScriptedSender(value -> Replies.toolText(value.startsWith("...")
                ? "{\"status\":\"ok\",\"responseTimeMs\":7}"
                : "{\"status\":\"ok\",\"responseTimeMs\":5}"));

        assertThat(differential.run(PROBE, sender)).isEmpty();
    }

    @Test
    void aContrastThatDiffersFromTheTraversalOnlyInATimingValueIsNotADifference() {
        ScriptedSender sender = new ScriptedSender(value -> switch (value) {
            case "..", "." -> Replies.toolText("{\"items\":[],\"tookMs\":" + value.length() + "}");
            default -> Replies.toolText("{\"error\":\"not found\"}");
        });

        assertThat(differential.run(BARE, sender)).isEmpty();
    }

    @Test
    void aDownstreamStatusThatDiffersOnlyInItsNumberIsStillADifference() {
        ScriptedSender sender = new ScriptedSender(value -> Replies.toolText(value.startsWith("...")
                ? "{\"ok\":false,\"message\":\"Request failed with status code 404\"}"
                : "{\"ok\":false,\"message\":\"Request failed with status code 403\"}"));

        assertThat(differential.run(PROBE, sender)).isPresent();
    }

    @Test
    void controlsThatDisagreeWithEachOtherAreNotADifference() {
        ScriptedSender sender = new ScriptedSender(RouteDifferentialTest::router)
                .then("..././", Replies.toolText("{\"error\":\"rate limited\"}"));

        assertThat(differential.run(PROBE, sender)).isEmpty();
    }

    @Test
    void traversalThatChangesOnRepeatIsNotADifference() {
        ScriptedSender sender = new ScriptedSender(RouteDifferentialTest::router)
                .then("../", Replies.toolText("{\"service\":\"index\"}"))
                .then("../", Replies.toolText("{\"service\":\"index\",\"t\":2}"));

        assertThat(differential.run(PROBE, sender)).isEmpty();
    }

    @Test
    void controlThatChangesOnRepeatIsNotADifference() {
        ScriptedSender sender = new ScriptedSender(RouteDifferentialTest::router)
                .then(".../", Replies.toolText("{\"error\":\"not found\"}"))
                .then(".../", Replies.toolText("{\"error\":\"timeout\"}"));

        assertThat(differential.run(PROBE, sender)).isEmpty();
    }

    @Test
    void controlToolErrorsStillCountAsAStableControlResult() {
        ScriptedSender sender = new ScriptedSender(value -> value.startsWith("...")
                ? Replies.toolError("no such license") : Replies.toolText("{\"service\":\"index\"}"));

        assertThat(differential.run(PROBE, sender)).isPresent();
    }

    @Test
    void traversalResultStatingARejectionIsNotADifference() {
        ScriptedSender sender = new ScriptedSender(value -> value.startsWith("...")
                ? Replies.toolText("{\"status\":404,\"detail\":\"missing\"}")
                : Replies.toolText("{\"status\":400,\"detail\":\"Invalid identifier: path separators are not allowed\"}"));

        assertThat(differential.run(PROBE, sender)).isEmpty();
    }

    @Test
    void validatorRejectingDotSegmentsInAnyWordingIsNotADifference() {
        ScriptedSender sender = new ScriptedSender(value -> List.of(value.split("/")).contains("..")
                ? Replies.toolText("{\"message\":\"segments must be plain names\"}")
                : Replies.toolText("{\"error\":\"not found\"}"));

        assertThat(differential.run(PROBE, sender)).isEmpty();
    }

    @Test
    void bareParentThatReachesADifferentRouteFromTheCurrentSegmentIsADifference() {
        ScriptedSender sender = new ScriptedSender(value -> switch (value) {
            case ".." -> Replies.toolText("{\"service\":\"index\"}");
            case "." -> Replies.toolText("{\"items\":[]}");
            default -> Replies.toolText("{\"error\":\"not found\"}");
        });

        assertThat(differential.run(BARE, sender)).isPresent();
        assertThat(sender.sent).containsExactly("..", "..", "...", "....", ".", "..", "...");
    }

    @Test
    void bareParentAnsweredLikeTheCurrentSegmentIsNotADifference() {
        ScriptedSender sender = new ScriptedSender(value -> value.length() <= 2
                ? Replies.toolText("{\"message\":\"segment may not be a dot segment\"}")
                : Replies.toolText("{\"error\":\"not found\"}"));

        assertThat(differential.run(BARE, sender)).isEmpty();
    }

    @Test
    void echoingToolIsNotADifference() {
        ScriptedSender sender = new ScriptedSender(value -> Replies.toolText("you asked for " + value));

        assertThat(differential.run(PROBE, sender)).isEmpty();
    }

    @Test
    void pathNormalisingToolIsNotADifference() {
        ScriptedSender sender = new ScriptedSender(value -> Replies.toolText(
                value.startsWith("...") ? "{\"normalized\":\"...\"}" : "{\"normalized\":\"..\"}"));

        assertThat(differential.run(PROBE, sender)).isEmpty();
    }

    private static burp.api.montoya.http.message.HttpRequestResponse router(String value) {
        return value.startsWith("...") ? Replies.toolText("{\"error\":\"not found\"}")
                : Replies.toolText("{\"service\":\"index\"}");
    }
}
