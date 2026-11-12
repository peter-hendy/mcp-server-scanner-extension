package com.mcpscanner.checks.api;

import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.issue.Cwe;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckDescriptorTest {

    private static CheckDescriptor descriptor(List<String> references) {
        return new CheckDescriptor(
                "id-1", "Display Name", "desc",
                CheckKind.REQUEST, AuditIssueSeverity.MEDIUM, AuditIssueConfidence.FIRM,
                true, references);
    }

    @Test
    void rejectsNullReferencesList() {
        assertThatThrownBy(() -> descriptor(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullEntryInsideReferences() {
        assertThatThrownBy(() -> descriptor(Arrays.asList("https://a", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankEntryInsideReferences() {
        assertThatThrownBy(() -> descriptor(List.of("https://a", "   ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyReferencesListIsAccepted() {
        CheckDescriptor descriptor = descriptor(List.of());

        assertThat(descriptor.references()).isEmpty();
    }

    @Test
    void referencesListIsDefensivelyCopied() {
        List<String> mutable = new ArrayList<>();
        mutable.add("https://example.com");
        CheckDescriptor descriptor = descriptor(mutable);

        mutable.add("https://added-after");

        assertThat(descriptor.references()).containsExactly("https://example.com");
        assertThatThrownBy(() -> descriptor.references().add("https://nope"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsMissingKind() {
        assertThatThrownBy(() -> new CheckDescriptor("id", "Name", "desc", null,
                AuditIssueSeverity.MEDIUM, AuditIssueConfidence.FIRM, true, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("kind");
    }

    @Test
    void rejectsMissingHeadlineConfidence() {
        assertThatThrownBy(() -> new CheckDescriptor("id", "Name", "desc", CheckKind.REQUEST,
                AuditIssueSeverity.MEDIUM, null, true, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("headlineConfidence");
    }

    @Test
    void rejectsBlankId() {
        assertThatThrownBy(() -> new CheckDescriptor(" ", "Name", "desc", CheckKind.REQUEST,
                AuditIssueSeverity.MEDIUM, AuditIssueConfidence.FIRM, true, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void carriesSeverityConfidenceKindReferencesAndCwes() {
        CheckDescriptor descriptor = new CheckDescriptor("id", "Name", "desc", CheckKind.HOST,
                AuditIssueSeverity.HIGH, AuditIssueConfidence.CERTAIN, false, List.of("https://ref"),
                "background", List.of(new Cwe(22, "Path Traversal")));

        assertThat(descriptor.kind()).isEqualTo(CheckKind.HOST);
        assertThat(descriptor.headlineSeverity()).isEqualTo(AuditIssueSeverity.HIGH);
        assertThat(descriptor.headlineConfidence()).isEqualTo(AuditIssueConfidence.CERTAIN);
        assertThat(descriptor.defaultEnabled()).isFalse();
        assertThat(descriptor.references()).containsExactly("https://ref");
        assertThat(descriptor.cwes()).containsExactly(new Cwe(22, "Path Traversal"));
    }

    @Test
    void issueNamePrefersTheBurpIssueNameOverTheDisplayName() {
        CheckDescriptor named = new CheckDescriptor("id", "Display", "desc", CheckKind.REQUEST,
                AuditIssueSeverity.LOW, AuditIssueConfidence.FIRM, true, List.of(), Optional.of("Burp name"));

        assertThat(named.issueName()).isEqualTo("Burp name");
        assertThat(descriptor(List.of()).issueName()).isEqualTo("Display Name");
    }
}
