package com.mcpscanner.access;

import java.util.Optional;
import java.util.Map;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.AccessFinding;
import com.mcpscanner.checks.api.AccessExpectation;
import com.mcpscanner.checks.api.AccessOutcome;
import com.mcpscanner.checks.api.PrincipalRole;
import com.mcpscanner.testutil.InMemoryPersistedObject;
import com.mcpscanner.config.ExtensionConfigStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.mcpscanner.access.AccessFixtures.ALICE;
import static com.mcpscanner.access.AccessFixtures.BOB;
import static com.mcpscanner.access.AccessFixtures.CAROL;
import static com.mcpscanner.access.AccessFixtures.DAVE;
import static com.mcpscanner.access.AccessFixtures.resource;
import static com.mcpscanner.access.AccessFixtures.resourceKey;
import static com.mcpscanner.access.AccessFixtures.tool;
import static com.mcpscanner.access.AccessFixtures.toolKey;
import static org.assertj.core.api.Assertions.assertThat;

class AccessMatrixTest {

    private static final Principal ALICE_AGAIN = new Principal("alice2", "alice2", PrincipalRole.PEER,
            ALICE.credentialHeaders(), Optional.empty(), Map.of());

    @Test
    void rowsAreTheUnionOfToolsThenResourcesAcrossEveryListingWithoutTemplatesOrPrompts() {
        AccessSnapshot snapshot = new AccessFixtures().compare(BOB)
                .lists(ALICE, List.of(tool("search")), List.of(resource("notes://alice")))
                .lists(BOB, List.of(tool("search"), tool("bob_only")), List.of(resource("notes://bob")))
                .matrix().snapshot();

        assertThat(snapshot.rows()).extracting(row -> row.item().key().name())
                .containsExactly("search", "bob_only", "notes://alice", "notes://bob");
    }

    @Test
    void primaryOwnsEveryItemItLists() {
        AccessRow row = singleRow(new AccessFixtures().compare(DAVE)
                .lists(ALICE, List.of(tool("search")), List.of())
                .lists(DAVE, List.of(tool("search")), List.of()));

        assertThat(row.owner()).isEqualTo(ALICE);
    }

    @Test
    void highestRoleListerOwnsAnItemThePrimaryDoesNotList() {
        AccessRow row = singleRow(new AccessFixtures().compare(BOB, DAVE)
                .lists(ALICE, List.of(), List.of())
                .lists(BOB, List.of(tool("admin")), List.of())
                .lists(DAVE, List.of(tool("admin")), List.of()));

        assertThat(row.owner()).isEqualTo(DAVE);
    }

    @Test
    void listingCellsShowListedHiddenAndNotDiscovered() {
        AccessRow row = singleRow(new AccessFixtures().compare(BOB, CAROL)
                .lists(ALICE, List.of(tool("search")), List.of())
                .lists(BOB, List.of(), List.of())
                .discovering(CAROL));

        assertThat(row.cell(ALICE.id()).listing()).isEqualTo(ListingState.LISTED);
        assertThat(row.cell(BOB.id()).listing()).isEqualTo(ListingState.HIDDEN);
        assertThat(row.cell(CAROL.id()).listing()).isEqualTo(ListingState.NOT_DISCOVERED);
        assertThat(row.anonymous().listing()).isEqualTo(ListingState.NO_LISTING);
    }

    @Test
    void itemEveryoneListsIsInferredForAnySignedInUser() {
        AccessRow row = singleRow(everyoneLists("search"));

        assertThat(row.expectation()).isEqualTo(AccessExpectation.ANY_SIGNED_IN);
        assertThat(row.inferred()).isTrue();
    }

    @Test
    void itemHiddenOnlyFromLowerIdentitiesIsInferredNotLower() {
        AccessRow row = singleRow(new AccessFixtures().compare(BOB, CAROL)
                .lists(ALICE, List.of(tool("delete")), List.of())
                .lists(BOB, List.of(tool("delete")), List.of())
                .lists(CAROL, List.of(), List.of()));

        assertThat(row.expectation()).isEqualTo(AccessExpectation.NOT_LOWER);
    }

    @Test
    void itemHiddenFromEveryOtherIdentityIsInferredOwnerOnly() {
        AccessRow row = singleRow(new AccessFixtures().compare(BOB, CAROL)
                .lists(ALICE, List.of(tool("list_users")), List.of())
                .lists(BOB, List.of(), List.of())
                .lists(CAROL, List.of(), List.of()));

        assertThat(row.expectation()).isEqualTo(AccessExpectation.OWNER_ONLY);
    }

    @Test
    void identitySharingTheOwnersCredentialsDoesNotWidenTheInference() {
        AccessRow row = singleRow(new AccessFixtures().compare(BOB, ALICE_AGAIN)
                .lists(ALICE, List.of(tool("list_users")), List.of())
                .lists(BOB, List.of(), List.of())
                .lists(ALICE_AGAIN, List.of(tool("list_users")), List.of()));

        assertThat(row.expectation()).isEqualTo(AccessExpectation.OWNER_ONLY);
    }

    @Test
    void itemListedOnlyByCopiesOfTheOwnerHasNoEvidenceAndIsInferredForAnySignedInUser() {
        AccessRow row = singleRow(new AccessFixtures().compare(ALICE_AGAIN)
                .lists(ALICE, List.of(tool("list_users")), List.of())
                .lists(ALICE_AGAIN, List.of(tool("list_users")), List.of()));

        assertThat(row.expectation()).isEqualTo(AccessExpectation.ANY_SIGNED_IN);
    }

    @Test
    void peerReachingAnOwnerOnlyItemStaysAnAnomalyBesideACopyOfTheOwner() {
        AccessMatrix matrix = new AccessFixtures().compare(BOB, ALICE_AGAIN)
                .lists(ALICE, List.of(tool("list_users")), List.of())
                .lists(BOB, List.of(), List.of())
                .lists(ALICE_AGAIN, List.of(tool("list_users")), List.of()).matrix();

        matrix.record(toolKey("list_users"), BOB.id(), AccessOutcome.SAME_RESULT);

        AccessRow row = matrix.snapshot().rows().get(0);
        assertThat(row.cell(BOB.id()).anomaly()).isTrue();
        assertThat(row.cell(ALICE_AGAIN.id()).anomaly()).isFalse();
    }

    @Test
    void lowerRoleIdentitySharingAHigherOwnersCredentialsDoesNotBlockHigherOnly() {
        Principal daveAgain = new Principal("dave2", "dave2", PrincipalRole.PEER,
                DAVE.credentialHeaders(), Optional.empty(), Map.of());
        AccessRow row = singleRow(new AccessFixtures().compare(BOB, DAVE, daveAgain)
                .lists(ALICE, List.of(), List.of())
                .lists(BOB, List.of(), List.of())
                .lists(DAVE, List.of(tool("admin")), List.of())
                .lists(daveAgain, List.of(tool("admin")), List.of()));

        assertThat(row.expectation()).isEqualTo(AccessExpectation.HIGHER_ONLY);
    }

    @Test
    void itemOnlyAHigherIdentityListsIsInferredHigherOnly() {
        AccessRow row = singleRow(new AccessFixtures().compare(BOB, DAVE)
                .lists(ALICE, List.of(), List.of())
                .lists(BOB, List.of(), List.of())
                .lists(DAVE, List.of(tool("admin")), List.of()));

        assertThat(row.expectation()).isEqualTo(AccessExpectation.HIGHER_ONLY);
    }

    @Test
    void identitiesNotYetDiscoveredDoNotNarrowTheInference() {
        AccessRow row = singleRow(new AccessFixtures().compare(BOB, CAROL)
                .lists(ALICE, List.of(tool("search")), List.of())
                .lists(BOB, List.of(tool("search")), List.of())
                .discovering(CAROL));

        assertThat(row.expectation()).isEqualTo(AccessExpectation.ANY_SIGNED_IN);
    }

    @Test
    void editedExpectationReplacesTheInferenceAndIsNoLongerInferred() {
        AccessMatrix matrix = everyoneLists("search").matrix();

        matrix.setExpectation(toolKey("search"), AccessExpectation.SHARED_BY_DESIGN);

        AccessRow row = matrix.snapshot().rows().get(0);
        assertThat(row.expectation()).isEqualTo(AccessExpectation.SHARED_BY_DESIGN);
        assertThat(row.inferred()).isFalse();
        assertThat(matrix.expectationFor(toolKey("search"))).isEqualTo(AccessExpectation.SHARED_BY_DESIGN);
    }

    @Test
    void editedExpectationsPersistPerEndpoint() {
        ExtensionConfigStore store = InMemoryPersistedObject.configStore();
        AtomicReference<String> endpoint = new AtomicReference<>("https://one.test/mcp");
        AccessFixtures fixtures = everyoneLists("search");
        fixtures.matrix(new PersistedAccessExpectations(store, endpoint::get))
                .setExpectation(toolKey("search"), AccessExpectation.OWNER_ONLY);

        AccessMatrix reloaded = fixtures.matrix(new PersistedAccessExpectations(store, endpoint::get));
        assertThat(reloaded.snapshot().rows().get(0).expectation()).isEqualTo(AccessExpectation.OWNER_ONLY);

        endpoint.set("https://two.test/mcp");
        assertThat(reloaded.snapshot().rows().get(0).inferred()).isTrue();
    }

    @Test
    void outcomesFillTheCellsOfTheirIdentityAndAnonymous() {
        AccessMatrix matrix = everyoneLists("search").matrix();

        matrix.record(toolKey("search"), BOB.id(), AccessOutcome.DIFFERENT_RESULT);
        matrix.recordAnonymous(toolKey("search"), AccessOutcome.REFUSED);

        AccessRow row = matrix.snapshot().rows().get(0);
        assertThat(row.cell(BOB.id()).outcome()).contains(AccessOutcome.DIFFERENT_RESULT);
        assertThat(row.cell(CAROL.id()).outcome()).isEmpty();
        assertThat(row.anonymous().outcome()).contains(AccessOutcome.REFUSED);
    }

    @Test
    void sameResultForAnIdentityTheExpectationForbidsIsAnAnomaly() {
        AccessMatrix matrix = new AccessFixtures().compare(BOB, CAROL)
                .lists(ALICE, List.of(tool("delete")), List.of())
                .lists(BOB, List.of(tool("delete")), List.of())
                .lists(CAROL, List.of(), List.of()).matrix();

        matrix.record(toolKey("delete"), BOB.id(), AccessOutcome.SAME_RESULT);
        matrix.record(toolKey("delete"), CAROL.id(), AccessOutcome.SAME_RESULT);

        AccessRow row = matrix.snapshot().rows().get(0);
        assertThat(row.cell(BOB.id()).anomaly()).isFalse();
        assertThat(row.cell(CAROL.id()).anomaly()).isTrue();
    }

    @Test
    void refusalNeverBreaksAnExpectation() {
        AccessMatrix matrix = new AccessFixtures().compare(BOB)
                .lists(ALICE, List.of(tool("list_users")), List.of())
                .lists(BOB, List.of(), List.of()).matrix();

        matrix.record(toolKey("list_users"), BOB.id(), AccessOutcome.REFUSED);

        assertThat(matrix.snapshot().rows().get(0).cell(BOB.id()).anomaly()).isFalse();
    }

    @Test
    void anOperationLevelFindingBreaksTheExpectation() {
        AccessMatrix matrix = everyoneLists("get_document").matrix();

        matrix.record(toolKey("get_document"), BOB.id(), AccessOutcome.SAME_RESULT);
        matrix.recordFinding(toolKey("get_document"), BOB.id(),
                new AccessFinding(AccessFinding.Kind.OPERATION_LEVEL, "Operation-level: bob"));

        AccessSnapshot snapshot = matrix.snapshot();
        assertThat(snapshot.rows().get(0).cell(BOB.id()).anomaly()).isTrue();
        assertThat(snapshot.anomalies()).isEqualTo(1);
        assertThat(snapshot.issues()).isEqualTo(1);
    }

    @Test
    void anObjectLevelFindingUnderAnySignedInIsAnIssueButNotAnExpectationBreach() {
        AccessMatrix matrix = everyoneLists("get_document").matrix();

        matrix.record(toolKey("get_document"), BOB.id(), AccessOutcome.SAME_RESULT);
        matrix.recordFinding(toolKey("get_document"), BOB.id(),
                new AccessFinding(AccessFinding.Kind.OBJECT_LEVEL, "Object-level: bob"));

        AccessSnapshot snapshot = matrix.snapshot();
        assertThat(snapshot.rows().get(0).cell(BOB.id()).anomaly()).isFalse();
        assertThat(snapshot.anomalies()).isZero();
        assertThat(snapshot.issues()).isEqualTo(1);
    }

    @Test
    void aPossibleFindingIsNeitherAnIssueNorABreach() {
        AccessMatrix matrix = everyoneLists("get_document").matrix();

        matrix.record(toolKey("get_document"), BOB.id(), AccessOutcome.SAME_RESULT);
        matrix.recordFinding(toolKey("get_document"), BOB.id(),
                new AccessFinding(AccessFinding.Kind.POSSIBLE_OBJECT_LEVEL, "Object-level: bob"));

        AccessRow row = matrix.snapshot().rows().get(0);
        assertThat(row.anomalies()).isZero();
        assertThat(row.issues()).isZero();
        assertThat(row.possibleFindings()).isEqualTo(1);
    }

    @Test
    void reRecordingAnOutcomeReplacesTheCellsEarlierFindings() {
        AccessMatrix matrix = everyoneLists("get_document").matrix();
        matrix.recordFinding(toolKey("get_document"), BOB.id(),
                new AccessFinding(AccessFinding.Kind.OBJECT_LEVEL, "Object-level: bob"));

        matrix.record(toolKey("get_document"), BOB.id(), AccessOutcome.REFUSED);

        assertThat(matrix.snapshot().rows().get(0).cell(BOB.id()).findings()).isEmpty();
    }

    @Test
    void anIdentityWithTheOwnersCredentialsIsShownAsNotCompared() {
        Principal sameAccount = new Principal("user", "User", PrincipalRole.PEER,
                () -> Map.of("authorization", "Bearer alice-token"), Optional.empty(), Map.of());
        AccessMatrix matrix = new AccessFixtures().compare(sameAccount)
                .lists(ALICE, List.of(tool("get_document")), List.of()).matrix();
        matrix.record(toolKey("get_document"), sameAccount.id(), AccessOutcome.SAME_RESULT);

        AccessCell cell = matrix.snapshot().rows().get(0).cell(sameAccount.id());
        assertThat(cell.sameCredentialsAs()).contains(ALICE);
        assertThat(cell.anomaly()).isFalse();
        assertThat(cell.checked()).isFalse();
    }

    @Test
    void aBreakWithoutAFindingIsAnAnomalyButNotAnIssue() {
        AccessMatrix matrix = new AccessFixtures().compare(BOB)
                .lists(ALICE, List.of(tool("list_users")), List.of())
                .lists(BOB, List.of(), List.of()).matrix();

        matrix.record(toolKey("list_users"), BOB.id(), AccessOutcome.SAME_RESULT);

        assertThat(matrix.snapshot().anomalies()).isEqualTo(1);
        assertThat(matrix.snapshot().issues()).isZero();
    }

    @Test
    void anonymousReachingTheOwnerResultIsAnAnomalyUnlessSharedByDesign() {
        AccessMatrix matrix = everyoneLists("search").matrix();
        matrix.recordAnonymous(toolKey("search"), AccessOutcome.SAME_RESULT);
        assertThat(matrix.snapshot().rows().get(0).anonymous().anomaly()).isTrue();

        matrix.setExpectation(toolKey("search"), AccessExpectation.SHARED_BY_DESIGN);
        assertThat(matrix.snapshot().rows().get(0).anonymous().anomaly()).isFalse();
    }

    @Test
    void differencesOnlyKeepsRowsWhoseListingsOrOutcomesDiffer() {
        AccessMatrix matrix = new AccessFixtures().compare(BOB)
                .lists(ALICE, List.of(tool("search"), tool("get"), tool("admin")), List.of())
                .lists(BOB, List.of(tool("search"), tool("get")), List.of()).matrix();
        matrix.record(toolKey("search"), BOB.id(), AccessOutcome.SAME_RESULT);
        matrix.record(toolKey("get"), BOB.id(), AccessOutcome.REFUSED);

        assertThat(matrix.snapshot().rows(AccessFilter.DIFFERENCES_ONLY)).extracting(row -> row.item().key().name())
                .containsExactly("get", "admin");
        assertThat(matrix.snapshot().rows(AccessFilter.ALL)).hasSize(3);
    }

    @Test
    void recordingNotifiesListenersAndClearingForgetsOutcomes() {
        AccessMatrix matrix = everyoneLists("search").matrix();
        AtomicInteger changes = new AtomicInteger();
        matrix.addListener(changes::incrementAndGet);

        matrix.record(toolKey("search"), BOB.id(), AccessOutcome.SAME_RESULT);
        matrix.clearOutcomes();

        assertThat(changes).hasValue(2);
        assertThat(matrix.snapshot().checked()).isFalse();
    }

    @Test
    void snapshotIsConnectedOnlyOnceThePrimaryListingIsKnown() {
        assertThat(new AccessFixtures().matrix().snapshot().connected()).isFalse();
        assertThat(everyoneLists("search").matrix().snapshot().connected()).isTrue();
    }

    @Test
    void columnsArePrimaryFirstThenComparedIdentities() {
        AccessSnapshot snapshot = everyoneLists("search").matrix().snapshot();

        assertThat(snapshot.principals()).containsExactly(ALICE, BOB, CAROL);
        assertThat(snapshot.comparedIdentities()).extracting(principal -> principal.role())
                .containsExactly(PrincipalRole.PEER, PrincipalRole.LOWER);
    }

    @Test
    void resourcesKeepTheirKind() {
        AccessRow row = singleRow(new AccessFixtures().lists(ALICE, List.of(), List.of(resource("notes://a"))));

        assertThat(row.item().key()).isEqualTo(resourceKey("notes://a"));
    }

    @Test
    void aSingleRowMatchesItsRowInTheSnapshot() {
        AccessMatrix matrix = new AccessFixtures().compare(BOB, CAROL)
                .lists(ALICE, List.of(tool("delete"), tool("search")), List.of(resource("notes://a")))
                .lists(BOB, List.of(tool("delete"), tool("search")), List.of())
                .lists(CAROL, List.of(tool("search")), List.of()).matrix();
        matrix.record(toolKey("delete"), CAROL.id(), AccessOutcome.SAME_RESULT);

        for (AccessRow row : matrix.snapshot().rows()) {
            assertThat(matrix.row(row.item().key())).contains(row);
        }
        assertThat(matrix.row(toolKey("unlisted"))).isEmpty();
    }

    private static AccessFixtures everyoneLists(String toolName) {
        return new AccessFixtures().compare(BOB, CAROL)
                .lists(ALICE, List.of(tool(toolName)), List.of())
                .lists(BOB, List.of(tool(toolName)), List.of())
                .lists(CAROL, List.of(tool(toolName)), List.of());
    }

    private static AccessRow singleRow(AccessFixtures fixtures) {
        return fixtures.matrix().snapshot().rows().get(0);
    }

    @Test
    void buildingRowsComparesHeldCredentialsWithoutAskingForFreshHeaders() {
        Principal refreshing = new Principal("oauth", "OAuth user", PrincipalRole.PEER, () -> {
            throw new AssertionError("headers() may refresh a token over the network");
        }, Optional.empty(), com.mcpscanner.checks.api.OwnedObjects.NONE, () -> Map.of("Authorization", "Bearer alice-token"));
        AccessMatrix matrix = new AccessFixtures().compare(refreshing)
                .lists(ALICE, List.of(tool("get_document")), List.of()).matrix();

        assertThat(matrix.snapshot().rows().get(0).cell(refreshing.id()).sameCredentialsAs()).contains(ALICE);
    }

    @Test
    void anIdentityDuplicatingAnEarlierComparedIdentityIsShownAsNotComparedAndIgnoredByInference() {
        Principal bobAgain = new Principal("bob2", "bob2", PrincipalRole.PEER,
                BOB.credentialHeaders(), Optional.empty(), Map.of());
        AccessMatrix matrix = new AccessFixtures().compare(BOB, CAROL, bobAgain)
                .lists(ALICE, List.of(tool("get_document")), List.of())
                .lists(BOB, List.of(), List.of())
                .lists(CAROL, List.of(), List.of())
                .lists(bobAgain, List.of(tool("get_document")), List.of()).matrix();
        matrix.record(toolKey("get_document"), bobAgain.id(), AccessOutcome.SAME_RESULT);

        AccessRow row = matrix.snapshot().rows().get(0);
        assertThat(row.cell(bobAgain.id()).sameCredentialsAs()).contains(BOB);
        assertThat(row.cell(bobAgain.id()).checked()).isFalse();
        assertThat(row.cell(BOB.id()).sameCredentialsAs()).isEmpty();
        assertThat(row.expectation()).isEqualTo(AccessExpectation.OWNER_ONLY);
    }
}
