package com.mcpscanner.checks.catalog.filetraversal;

import com.mcpscanner.checks.catalog.filetraversal.SimulatedFileTool.Policy;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RootBoundaryProbeTest {

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void reportsASiblingThatReachedTheFilesystemAfterTheControlWasDenied() {
        SimulatedFileTool tool = new SimulatedFileTool(Policy.PREFIX_MATCH);

        Optional<Finding> finding = probe(tool, "readme.txt").run("mcpscan-9");

        assertThat(finding).hasValueSatisfying(value -> {
            assertThat(value.kind()).isEqualTo(FindingKind.ROOT_BOUNDARY_BYPASS);
            assertThat(value.evidence()).hasSize(2);
        });
        assertThat(tool.sent()).extracting(PathArgumentInsertionPointTestSupport::pathOf)
                .containsExactly("/mcpscan-9/missing", "/srv/workspace_mcpscan-9/missing");
    }

    @Test
    void staysSilentWhenTheSiblingIsDeniedToo() {
        assertThat(probe(new SimulatedFileTool(Policy.CONTAINED), "readme.txt").run("mcpscan-9")).isEmpty();
    }

    @Test
    void sendsOnlyTheControlWhenNoRootIsNamed() {
        SimulatedFileTool tool = new SimulatedFileTool(Policy.UNRESTRICTED);

        assertThat(probe(tool, "readme.txt").run("mcpscan-9")).isEmpty();
        assertThat(tool.sent()).hasSize(1);
    }

    @Test
    void keepsTheBaseValueUriSchemeOnBothProbes() {
        SimulatedFileTool tool = new SimulatedFileTool(Policy.UNRESTRICTED);

        probe(tool, "file:///srv/workspace/readme.txt").run("mcpscan-9");

        assertThat(tool.sent()).extracting(PathArgumentInsertionPointTestSupport::pathOf)
                .containsExactly("file:///mcpscan-9/missing");
    }

    private static RootBoundaryProbe probe(SimulatedFileTool tool, String baseValue) {
        return new RootBoundaryProbe(new PayloadSender(tool.http(), new PathArgumentInsertionPoint(baseValue), Optional::empty));
    }
}
