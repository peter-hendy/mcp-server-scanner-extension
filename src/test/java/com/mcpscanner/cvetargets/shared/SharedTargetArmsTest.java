package com.mcpscanner.cvetargets.shared;

import com.mcpscanner.cvetargets.manifest.Download;
import com.mcpscanner.cvetargets.manifest.LoadedTarget;
import com.mcpscanner.cvetargets.manifest.ManifestFixtures;
import com.mcpscanner.cvetargets.manifest.SeedRequest;
import com.mcpscanner.cvetargets.manifest.TargetManifest;
import com.mcpscanner.cvetargets.manifest.TargetRepository;
import com.mcpscanner.cvetargets.manifest.TargetSelection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SharedTargetArmsTest {

    @TempDir
    Path root;

    @Test
    void bringsEachArmOfABuildSeveralChecksShareUpOnceForAllOfThem() {
        ManifestFixtures.writeValidTarget(root, "check-a", "target-one");
        ManifestFixtures.writeValidTarget(root, "check-b", "target-two");

        List<SharedTargetArm> shared = SharedTargetArms.of(loadAll());

        assertThat(shared).hasSize(2);
        assertThat(shared.get(0).members()).extracting(member -> member.target().manifest().check(),
                member -> member.arm().name())
                .containsExactly(tuple("check-a", "vulnerable"), tuple("check-b", "vulnerable"));
        assertThat(shared.get(1).members()).extracting(member -> member.target().manifest().check(),
                member -> member.arm().name())
                .containsExactly(tuple("check-a", "fixed"), tuple("check-b", "fixed"));
    }

    @Test
    void keepsTargetsWhoseBuildFilesDifferApart() throws IOException {
        ManifestFixtures.writeValidTarget(root, "check-a", "target-one");
        Path other = ManifestFixtures.writeValidTarget(root, "check-b", "target-two");
        Files.writeString(other.resolve("Dockerfile"), "FROM scratch\n");

        assertThat(SharedTargetArms.of(loadAll())).hasSize(4).allSatisfy(arm -> assertThat(arm.members()).hasSize(1));
    }

    @Test
    void neverGroupsTargetsWhoseComposeReachesOutsideTheirDirectory() throws IOException {
        Path one = ManifestFixtures.writeValidTarget(root, "check-a", "target-one");
        Path two = ManifestFixtures.writeValidTarget(root, "check-b", "target-two");
        String outside = "services:\n  vulnerable:\n    build:\n      context: ../../_shared/server\n";
        Files.writeString(one.resolve("compose.yaml"), outside);
        Files.writeString(two.resolve("compose.yaml"), outside);

        assertThat(SharedTargetArms.of(loadAll())).hasSize(4);
    }

    @Test
    void neverGroupsTargetsThatHoldASymbolicLink() throws IOException {
        Path shared = Files.writeString(root.resolve("shared-fixture.txt"), "fixture");
        Path one = ManifestFixtures.writeValidTarget(root, "check-a", "target-one");
        Path two = ManifestFixtures.writeValidTarget(root, "check-b", "target-two");
        Files.createSymbolicLink(one.resolve("fixture.txt"), shared);
        Files.createSymbolicLink(two.resolve("fixture.txt"), shared);

        assertThat(SharedTargetArms.of(loadAll())).hasSize(4);
    }

    @Test
    void keepsTargetsThatAuthenticateDifferentlyApart() {
        ManifestFixtures.writeValidTarget(root, "check-a", "target-one");
        ManifestFixtures.writeTarget(root, "check-b", "target-two", manifest("check-b", "target-two")
                .replace("\"auth\": { \"scheme\": \"none\" },", "\"auth\": { \"scheme\": \"none\", "
                        + "\"headers\": { \"X-Upstream\": \"http://upstream.test\" } },"));

        assertThat(SharedTargetArms.of(loadAll())).hasSize(4);
    }

    @Test
    void keepsTargetsThatDeclareDifferentIdentitiesApart() {
        ManifestFixtures.writeValidTarget(root, "check-a", "target-one");
        ManifestFixtures.writeTarget(root, "check-b", "target-two", manifest("check-b", "target-two")
                .replace("\"auth\": { \"scheme\": \"none\" },", "\"auth\": { \"scheme\": \"none\" }, "
                        + "\"identities\": { \"primary\": { \"name\": \"alice\" } },"));

        assertThat(SharedTargetArms.of(loadAll())).hasSize(4);
    }

    @Test
    void theSharedArmRunsEveryMembersSeedsWithTheMostGenerousLimits() {
        ManifestFixtures.writeTarget(root, "check-a", "target-one", manifest("check-a", "target-one")
                .replace("\"auth\": { \"scheme\": \"none\" },", "\"auth\": { \"scheme\": \"none\" }, "
                        + "\"readiness\": { \"probe\": \"mcp-initialize\", \"timeoutSeconds\": 180 }, "
                        + "\"footprintMb\": 1024, " + seeds("a.md")));
        ManifestFixtures.writeTarget(root, "check-b", "target-two", manifest("check-b", "target-two")
                .replace("\"auth\": { \"scheme\": \"none\" },", "\"auth\": { \"scheme\": \"none\" }, "
                        + "\"armTimeoutSeconds\": 3000, \"readTimeoutSeconds\": 30, " + seeds("b.md")));

        LoadedTarget runnable = SharedTargetArms.of(loadAll()).get(0).runnable();

        TargetManifest manifest = runnable.manifest();
        assertThat(runnable.directory()).isEqualTo(root.resolve("check-a").resolve("target-one"));
        assertThat(manifest.id()).isEqualTo("target-one");
        assertThat(manifest.arms()).extracting(arm -> arm.name()).containsExactly("vulnerable");
        assertThat(manifest.seeds()).extracting(seed -> seed.params().path("arguments").path("path").asText())
                .containsExactly("a.md", "b.md");
        assertThat(manifest.readiness().timeoutSeconds()).isEqualTo(180);
        assertThat(manifest.armTimeoutSeconds()).isEqualTo(3000);
        assertThat(manifest.readTimeoutSeconds()).isEqualTo(30);
        assertThat(manifest.footprintMb()).isEqualTo(1024);
    }

    @Test
    void runsASeedTwoMembersShareOnlyOnce() {
        ManifestFixtures.writeTarget(root, "check-a", "target-one", manifest("check-a", "target-one")
                .replace("\"auth\": { \"scheme\": \"none\" },", "\"auth\": { \"scheme\": \"none\" }, " + seeds("a.md")));
        ManifestFixtures.writeTarget(root, "check-b", "target-two", manifest("check-b", "target-two")
                .replace("\"auth\": { \"scheme\": \"none\" },", "\"auth\": { \"scheme\": \"none\" }, " + seeds("a.md")));

        List<SeedRequest> seeds = SharedTargetArms.of(loadAll()).get(0).runnable().manifest().seeds();

        assertThat(seeds).hasSize(1);
    }

    @Test
    void keepsTargetsThatDownloadDifferentArtifactsApart() {
        ManifestFixtures.writeValidTarget(root, "check-a", "target-one");
        ManifestFixtures.writeTarget(root, "check-b", "target-two", manifest("check-b", "target-two")
                .replace("\"auth\": { \"scheme\": \"none\" },", "\"auth\": { \"scheme\": \"none\" }, " + downloads()));

        assertThat(SharedTargetArms.of(loadAll())).hasSize(4);
    }

    @Test
    void theSharedArmKeepsTheDownloadsItsMembersShare() {
        ManifestFixtures.writeTarget(root, "check-a", "target-one", manifest("check-a", "target-one")
                .replace("\"auth\": { \"scheme\": \"none\" },", "\"auth\": { \"scheme\": \"none\" }, " + downloads()));
        ManifestFixtures.writeTarget(root, "check-b", "target-two", manifest("check-b", "target-two")
                .replace("\"auth\": { \"scheme\": \"none\" },", "\"auth\": { \"scheme\": \"none\" }, " + downloads()));

        List<SharedTargetArm> shared = SharedTargetArms.of(loadAll());

        assertThat(shared).hasSize(2);
        assertThat(shared.get(0).runnable().manifest().downloads()).extracting(Download::file)
                .containsExactly("package.tgz");
    }

    private List<LoadedTarget> loadAll() {
        return new TargetRepository(root).load(TargetSelection.everything());
    }

    private static String manifest(String check, String target) {
        return ManifestFixtures.VALID_MANIFEST.formatted(target, check);
    }

    private static String downloads() {
        return "\"downloads\": [ { \"file\": \"package.tgz\", \"url\": \"https://registry.example.test/package.tgz\", "
                + "\"integrity\": \"sha512-" + Base64.getEncoder().encodeToString(new byte[64]) + "\" } ],";
    }

    private static String seeds(String path) {
        return "\"seeds\": [ { \"method\": \"tools/call\", \"params\": { \"name\": \"read\", "
                + "\"arguments\": { \"path\": \"" + path + "\" } } } ],";
    }
}
