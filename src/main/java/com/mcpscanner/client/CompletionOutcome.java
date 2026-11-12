package com.mcpscanner.client;

import java.util.List;

public sealed interface CompletionOutcome {

    int MAX_VALUES = 100;
    int MAX_VALUE_LENGTH = 1_000;

    record Values(List<String> values, Integer total, Boolean hasMore) implements CompletionOutcome {
        public Values {
            values = values.stream().limit(MAX_VALUES).map(CompletionOutcome::bounded).toList();
        }
    }

    record Failed(String reason) implements CompletionOutcome {
        public Failed {
            reason = bounded(reason);
        }
    }

    record NotAdvertised() implements CompletionOutcome {
    }

    private static String bounded(String text) {
        return text.length() <= MAX_VALUE_LENGTH ? text : text.substring(0, MAX_VALUE_LENGTH) + "…";
    }
}
