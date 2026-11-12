package com.mcpscanner.client;

import java.util.List;
import java.util.Optional;

public record ListOutcome<T>(List<T> items, Optional<ListError> error, Optional<ListTruncation> truncation) {

    public ListOutcome {
        items = List.copyOf(items);
    }

    public static <T> ListOutcome<T> complete(List<T> items) {
        return new ListOutcome<>(items, Optional.empty(), Optional.empty());
    }

    public static <T> ListOutcome<T> truncated(List<T> items, ListTruncation truncation) {
        return new ListOutcome<>(items, Optional.empty(), Optional.of(truncation));
    }

    public static <T> ListOutcome<T> failed(List<T> items, ListError error) {
        return new ListOutcome<>(items, Optional.of(error), Optional.empty());
    }
}
