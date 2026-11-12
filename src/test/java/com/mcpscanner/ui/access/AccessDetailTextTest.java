package com.mcpscanner.ui.access;

import com.mcpscanner.access.AccessFixtures;
import com.mcpscanner.access.AccessMatrix;
import com.mcpscanner.access.AccessRow;
import com.mcpscanner.checks.api.AccessFinding;
import com.mcpscanner.checks.api.AccessOutcome;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRole;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.mcpscanner.access.AccessFixtures.ALICE;
import static com.mcpscanner.access.AccessFixtures.BOB;
import static com.mcpscanner.access.AccessFixtures.readOnlyTool;
import static com.mcpscanner.access.AccessFixtures.toolKey;
import static org.assertj.core.api.Assertions.assertThat;

class AccessDetailTextTest {

    private static final AccessFinding OBJECT_LEVEL = new AccessFinding(AccessFinding.Kind.OBJECT_LEVEL,
            "Object-level: bob received Primary's private marker");
    private static final AccessFinding POSSIBLE = new AccessFinding(AccessFinding.Kind.POSSIBLE_OBJECT_LEVEL,
            "Object-level: bob received Primary's result for the same arguments (Tentative — no private marker "
                    + "configured for Primary)");

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void anObjectLevelIssueUnderAnySignedInIsNamedWithoutClaimingAnExpectationBreach() {
        AccessMatrix matrix = everyoneListsSearch(BOB);
        matrix.record(toolKey("search"), BOB.id(), AccessOutcome.SAME_RESULT);
        matrix.recordFinding(toolKey("search"), BOB.id(), OBJECT_LEVEL);

        String detail = identityDetail(matrix, BOB);

        assertThat(detail).contains("Object-level: bob received Primary's private marker. Reported in Burp's site map.")
                .contains("Consistent with the expectation")
                .doesNotContain("Breaks the expectation");
    }

    @Test
    void anOperationLevelIssueBreaksTheExpectationAndPointsToTheSiteMap() {
        AccessMatrix matrix = everyoneListsSearch(BOB);
        matrix.record(toolKey("search"), BOB.id(), AccessOutcome.SAME_RESULT);
        matrix.recordFinding(toolKey("search"), BOB.id(),
                new AccessFinding(AccessFinding.Kind.OPERATION_LEVEL, "Operation-level: bob reached it"));

        assertThat(identityDetail(matrix, BOB))
                .contains("→ Breaks the expectation: the check reported an issue in Burp's site map.")
                .contains("Operation-level: bob reached it. Reported in Burp's site map.");
    }

    @Test
    void aPossibleFindingIsShownAsUnconfirmedAndNotFiled() {
        AccessMatrix matrix = everyoneListsSearch(BOB);
        matrix.record(toolKey("search"), BOB.id(), AccessOutcome.SAME_RESULT);
        matrix.recordFinding(toolKey("search"), BOB.id(), POSSIBLE);

        assertThat(identityDetail(matrix, BOB)).contains("Possible — " + POSSIBLE.summary()
                + ". Not reported in Burp's site map: set a private marker for Primary on the Users tab to "
                + "confirm.");
        assertThat(AccessCellText.identity(row(matrix).cell(BOB.id()), false)).endsWith(" · ? possible leak");
    }

    @Test
    void aSameResultWithoutAnOwnerMarkerSuggestsSettingOne() {
        AccessMatrix matrix = everyoneListsSearch(BOB);
        matrix.record(toolKey("search"), BOB.id(), AccessOutcome.SAME_RESULT);

        assertThat(identityDetail(matrix, BOB))
                .contains("Set a private marker for Primary on the Users tab to detect data leaks.");
    }

    @Test
    void aSameResultWithAnOwnerMarkerNeedsNoHint() {
        Principal marked = new Principal(Principal.PRIMARY_ID, Principal.PRIMARY_NAME, PrincipalRole.PRIMARY,
                ALICE.credentialHeaders(), Optional.of("ALICE-PRIVATE"), Map.of());
        AccessMatrix matrix = new AccessFixtures().withPrimary(marked).compare(BOB)
                .lists(marked, List.of(readOnlyTool("search")), List.of())
                .lists(BOB, List.of(readOnlyTool("search")), List.of()).matrix();
        matrix.record(toolKey("search"), BOB.id(), AccessOutcome.SAME_RESULT);

        assertThat(identityDetail(matrix, BOB)).doesNotContain("private marker");
    }

    @Test
    void anIdentityWithTheOwnersCredentialsIsShownAsNotCompared() {
        Principal sameAccount = new Principal("user", "User", PrincipalRole.PEER, ALICE.credentialHeaders(),
                Optional.empty(), Map.of());
        AccessMatrix matrix = everyoneListsSearch(sameAccount);

        assertThat(identityDetail(matrix, sameAccount))
                .contains("Same credentials as Primary — not compared. Give User another account's credentials "
                        + "on the Users tab to compare it.")
                .doesNotContain("Expected:");
        assertThat(AccessCellText.identity(row(matrix).cell(sameAccount.id()), false))
                .isEqualTo("● listed · = same credentials as Primary");
    }

    @Test
    void anIdentityDuplicatingAnEarlierComparedIdentityNamesThatIdentity() {
        Principal bobAgain = new Principal("bob2", "Bob again", PrincipalRole.PEER, BOB.credentialHeaders(),
                Optional.empty(), Map.of());
        AccessMatrix matrix = new AccessFixtures().compare(BOB, bobAgain)
                .lists(ALICE, List.of(readOnlyTool("search")), List.of())
                .lists(BOB, List.of(readOnlyTool("search")), List.of())
                .lists(bobAgain, List.of(readOnlyTool("search")), List.of()).matrix();

        assertThat(identityDetail(matrix, bobAgain))
                .contains("Same credentials as " + BOB.name() + " — not compared. Give Bob again another account's "
                        + "credentials on the Users tab to compare it.");
        assertThat(AccessCellText.identity(row(matrix).cell(bobAgain.id()), false))
                .isEqualTo("● listed · = same credentials as " + BOB.name());
    }

    @Test
    void theItemSummaryCountsBreachesIssuesAndPossibleFindingsSeparately() {
        AccessMatrix matrix = everyoneListsSearch(BOB);
        matrix.record(toolKey("search"), BOB.id(), AccessOutcome.SAME_RESULT);
        matrix.recordFinding(toolKey("search"), BOB.id(), OBJECT_LEVEL);
        matrix.recordFinding(toolKey("search"), BOB.id(), POSSIBLE);

        assertThat(AccessDetailText.describe(row(matrix), new AccessColumn.Item()))
                .contains("No identity reaches it against the expectation. 1 issue reported in Burp's site map. "
                        + "1 possible leak to confirm with a private marker.");
    }

    private static AccessMatrix everyoneListsSearch(Principal compared) {
        return new AccessFixtures().compare(compared)
                .lists(ALICE, List.of(readOnlyTool("search")), List.of())
                .lists(compared, List.of(readOnlyTool("search")), List.of()).matrix();
    }

    private static AccessRow row(AccessMatrix matrix) {
        return matrix.snapshot().rows().get(0);
    }

    private static String identityDetail(AccessMatrix matrix, Principal principal) {
        return AccessDetailText.describe(row(matrix), new AccessColumn.Identity(principal));
    }
}
