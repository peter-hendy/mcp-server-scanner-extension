package com.mcpscanner.ui;

import com.mcpscanner.scan.ScanTarget;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

record ScanItemBinding<T>(InventoryScanner scanner, Function<T, ScanTarget> targetFor) {

    ScanItemBinding {
        Objects.requireNonNull(scanner);
        Objects.requireNonNull(targetFor);
    }

    Optional<String> blocker(T item) {
        return scanner.blocker(targetFor.apply(item));
    }

    void scan(T item) {
        scanner.scan(targetFor.apply(item));
    }
}
