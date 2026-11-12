package com.mcpscanner.cvetargets.environment;

import com.mcpscanner.cvetargets.manifest.LoadedTarget;
import com.mcpscanner.cvetargets.manifest.ManifestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ComposeProjectTest {

    @TempDir
    Path firstWorktree;

    @TempDir
    Path secondWorktree;

    @Test
    void namesTheProjectAfterItsRunOriginCheckTargetAndArm() {
        assertThat(projectIn(firstWorktree).name()).matches("cvet-[0-9a-f]{8}-check-a-target-one-vulnerable");
    }

    @Test
    void separatesTheSameTargetArmUnderTwoChecksSoTheyCanRunAtOnce() {
        assertThat(projectIn(firstWorktree, "check-a").name())
                .isNotEqualTo(projectIn(firstWorktree, "check-b").name());
    }

    @Test
    void keepsTheSameNameForTheSameOriginSoAStaleRunIsStillCleaned() {
        assertThat(projectIn(firstWorktree).name()).isEqualTo(projectIn(firstWorktree).name());
    }

    @Test
    void separatesTheSameTargetArmRunFromDifferentWorktrees() {
        assertThat(projectIn(firstWorktree).name()).isNotEqualTo(projectIn(secondWorktree).name());
    }

    @Test
    void keepsDownloadsInTheCacheBesideTheTargetsAndNamesItInTheEnvironmentFile() throws Exception {
        ComposeProject project = projectIn(firstWorktree);
        Path downloads = firstWorktree.toRealPath().resolve(".cache").resolve("downloads");

        assertThat(project.downloadsDirectory()).isEqualTo(downloads);
        assertThat(project.environmentFileContent())
                .contains(ComposeProject.DOWNLOADS_VARIABLE + "=" + downloads.toAbsolutePath() + "\n");
    }

    private static ComposeProject projectIn(Path targetsRoot) {
        return projectIn(targetsRoot, "check-a");
    }

    private static ComposeProject projectIn(Path targetsRoot, String check) {
        ManifestFixtures.writeValidTarget(targetsRoot, check, "target-one");
        LoadedTarget target = ManifestFixtures.loadOnly(targetsRoot, check, "target-one");
        return ComposeProject.forArm(target, target.manifest().arms().get(0));
    }
}
