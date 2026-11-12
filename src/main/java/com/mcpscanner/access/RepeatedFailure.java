package com.mcpscanner.access;

import java.util.Optional;

final class RepeatedFailure {

    private Optional<String> previous = Optional.empty();

    Optional<String> repeatedBy(Optional<String> failure) {
        Optional<String> repeated = failure.filter(reason -> previous.filter(reason::equals).isPresent());
        previous = failure;
        return repeated;
    }
}
