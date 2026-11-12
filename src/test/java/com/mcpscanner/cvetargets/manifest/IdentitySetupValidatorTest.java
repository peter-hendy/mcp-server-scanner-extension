package com.mcpscanner.cvetargets.manifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentitySetupValidatorTest {

    private static final String ITEM = """
            { "seed": { "method": "%s", "params": { "name": "get_note", "arguments": {} } },
              "expectation": "owner_only",
              "groundTruth": { "%s": { "%s": "same" } } }
            """;

    @TempDir
    Path root;

    @Test
    void acceptsIdentitiesWithItemsAndGroundTruthPerArm() {
        LoadedTarget target = load(identities("""
                "others": [ { "id": "bob", "role": "peer", "headers": { "Authorization": "Bearer b" } } ],
                "items": [ %s ]
                """.formatted(ITEM.formatted("tools/call", "vulnerable", "bob@primary-session"))));

        assertThat(target.manifest().identities().others()).singleElement()
                .satisfies(bob -> assertThat(bob.name()).isEqualTo("bob"));
        assertThat(target.manifest().baseSeeds()).hasSize(1);
    }

    @Test
    void rejectsReservedDuplicateAndPrimaryRoleIdentities() {
        assertInvalid(identities("""
                "others": [ { "id": "anonymous", "role": "peer" }, { "id": "bob", "role": "peer" },
                            { "id": "bob", "role": "lower" }, { "id": "carol", "role": "primary" }, { "id": "dan" } ]
                """),
                "identities: id anonymous is reserved",
                "identities: id bob is duplicated",
                "identities: carol needs role peer, lower or higher",
                "identities: dan needs role peer, lower or higher");
    }

    @Test
    void rejectsItemsThatAreNotToolCallsOrResourceReads() {
        assertInvalid(identities("""
                "others": [ { "id": "bob", "role": "peer" } ],
                "items": [ %s ]
                """.formatted(ITEM.formatted("prompts/get", "vulnerable", "bob"))),
                "identities: every item seed must be tools/call or resources/read");
    }

    @Test
    void rejectsGroundTruthForUnknownArmsOrCallers() {
        assertInvalid(identities("""
                "others": [ { "id": "bob", "role": "peer" } ],
                "items": [ %s, %s ]
                """.formatted(ITEM.formatted("tools/call", "staging", "bob"),
                ITEM.formatted("tools/call", "fixed", "mallory"))),
                "identities: ground truth names unknown arm staging",
                "identities: ground truth names unknown caller mallory");
    }

    @Test
    void rejectsAnItemSentAsAnUndeclaredIdentity() {
        assertInvalid(identities("""
                "others": [ { "id": "bob", "role": "higher" } ],
                "items": [ { "seed": { "method": "tools/call", "params": { "name": "x" }, "asIdentity": "carol" } } ]
                """),
                "identities: item seed asIdentity carol is not a declared identity");
    }

    @Test
    void rejectsAMissingProvisioningScript() {
        assertInvalid(identities("\"provision\": \"provision.py\""),
                "identities: provision script provision.py does not exist in the target directory");
    }

    private void assertInvalid(String manifest, String... violations) {
        ManifestFixtures.writeTarget(root, "check-a", "target-one", manifest);
        assertThatThrownBy(() -> new TargetRepository(root).load(TargetSelection.everything()))
                .isInstanceOf(InvalidTargetException.class)
                .hasMessageContainingAll(violations);
    }

    private LoadedTarget load(String manifest) {
        ManifestFixtures.writeTarget(root, "check-a", "target-one", manifest);
        return ManifestFixtures.loadOnly(root, "check-a", "target-one");
    }

    private static String identities(String body) {
        return ManifestFixtures.VALID_MANIFEST.formatted("target-one", "check-a")
                .replace("\"arms\": [", "\"identities\": { " + body + " },\n  \"arms\": [");
    }
}
