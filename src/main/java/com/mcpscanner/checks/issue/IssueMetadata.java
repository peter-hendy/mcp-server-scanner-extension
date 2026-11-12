package com.mcpscanner.checks.issue;

import java.util.List;

public record IssueMetadata(String background,
                            String remediation,
                            List<Cwe> cwes,
                            List<String> references) {

    public IssueMetadata {
        cwes = List.copyOf(cwes);
        references = List.copyOf(references);
    }
}
