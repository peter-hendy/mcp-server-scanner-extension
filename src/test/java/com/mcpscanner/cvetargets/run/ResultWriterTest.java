package com.mcpscanner.cvetargets.run;

import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpscanner.cvetargets.manifest.ArmRole;
import com.mcpscanner.cvetargets.manifest.ExpectedOutcome;
import com.mcpscanner.cvetargets.manifest.Outcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResultWriterTest {

    @TempDir
    Path root;

    @Test
    void recordsEachIssuesDetailSoAVerdictCanBeAdjudicated() throws Exception {
        IssueSummary issue = new IssueSummary("Route escape", AuditIssueSeverity.INFORMATION,
                AuditIssueConfidence.TENTATIVE, "http://127.0.0.1/mcp", "<p>field params.arguments.id</p>");
        ArmResult result = new ArmResult("example-check", "example-target", "fixed", "direct", ArmRole.FIXED, "1.0.1",
                ExpectedOutcome.of(Outcome.SILENT), Outcome.FIRE, false, "expected SILENT", List.of(issue), 1, 0,
                new ProbeTally(4, 4), 10, "2026-11-06T12:00:00Z", null, null, null, List.of(), null, null);

        JsonNode json = new ObjectMapper().readTree(new ResultWriter(root).write(result).toFile());

        assertThat(json.path("issues").get(0).path("detail").asText()).isEqualTo("<p>field params.arguments.id</p>");
    }

    @Test
    void writesOneJsonFilePerCheckTargetAndArm() throws Exception {
        ArmResult result = new ArmResult("example-check", "example-target", "fixed", "direct", ArmRole.FIXED, "1.0.1",
                ExpectedOutcome.of(Outcome.SILENT), Outcome.SILENT, true, "expected SILENT", List.of(), 11, 2,
                new ProbeTally(40, 38), 1750,
                "2026-11-06T12:00:00Z", null, null, null, List.of(), Map.of("audit", 1200L),
                new PeakUsage(900, 0, 140));

        Path file = new ResultWriter(root).write(result);

        assertThat(file).isEqualTo(root.resolve("example-check/example-target/fixed.json"));
        JsonNode json = new ObjectMapper().readTree(file.toFile());
        assertThat(json.path("check").asText()).isEqualTo("example-check");
        assertThat(json.path("mode").asText()).isEqualTo("direct");
        assertThat(json.path("observed").asText()).isEqualTo("SILENT");
        assertThat(json.path("passed").asBoolean()).isTrue();
        assertThat(json.path("durationMillis").asLong()).isEqualTo(1750);
        assertThat(json.path("unansweredBaseRequestCount").asInt()).isEqualTo(2);
        assertThat(json.path("issues").isArray()).isTrue();
        assertThat(json.path("probes").path("sent").asInt()).isEqualTo(40);
        assertThat(json.path("probes").path("reached").asInt()).isEqualTo(38);
        assertThat(json.path("phases").path("audit").asLong()).isEqualTo(1200);
        assertThat(json.path("peakUsage").path("harnessRssMb").asLong()).isEqualTo(900);
        assertThat(json.path("peakUsage").path("containersMb").asLong()).isEqualTo(140);
    }
}
