package com.mcpscanner.cvetargets.manifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TargetSelectionTest {

    @Test
    void selectsAnyOfACommaSeparatedListOfTargets() {
        TargetSelection selection = new TargetSelection(Optional.empty(), Optional.of("alpha, beta"));

        assertThat(selection.includes(manifest("check-a", "alpha"))).isTrue();
        assertThat(selection.includes(manifest("check-b", "beta"))).isTrue();
        assertThat(selection.includes(manifest("check-a", "gamma"))).isFalse();
    }

    @Test
    void selectsAnyOfACommaSeparatedListOfChecks() {
        TargetSelection selection = new TargetSelection(Optional.of("check-a,check-b"), Optional.empty());

        assertThat(selection.includes(manifest("check-b", "alpha"))).isTrue();
        assertThat(selection.includes(manifest("check-c", "alpha"))).isFalse();
    }

    @Test
    void aSingleNameStillSelectsExactly() {
        TargetSelection selection = new TargetSelection(Optional.of("check-a"), Optional.of("alpha"));

        assertThat(selection.includes(manifest("check-a", "alpha"))).isTrue();
        assertThat(selection.includes(manifest("check-a", "alphabet"))).isFalse();
    }

    @TempDir
    Path root;

    private TargetManifest manifest(String check, String id) {
        ManifestFixtures.writeValidTarget(root, check, id);
        return ManifestFixtures.loadOnly(root, check, id).manifest();
    }
}
