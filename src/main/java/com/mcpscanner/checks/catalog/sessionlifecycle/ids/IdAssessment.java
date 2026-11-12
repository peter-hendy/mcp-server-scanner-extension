package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

import java.util.List;

public record IdAssessment(List<String> weaknesses, boolean undecided) {

    public IdAssessment {
        weaknesses = List.copyOf(weaknesses);
    }
}
