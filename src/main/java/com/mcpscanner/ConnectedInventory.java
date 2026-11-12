package com.mcpscanner;

import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.client.PrimaryConnection;
import com.mcpscanner.scan.ScanInventory;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

final class ConnectedInventory implements Supplier<Optional<ScanInventory>> {

    private final AtomicReference<Optional<ScanInventory>> current = new AtomicReference<>(Optional.empty());

    void connected(PrimaryConnection connection) {
        ConnectResult catalog = connection.catalog();
        current.set(Optional.of(new ScanInventory(catalog.tools(), catalog.resources(), catalog.resourceTemplates(),
                catalog.prompts())));
    }

    void disconnected() {
        current.set(Optional.empty());
    }

    @Override
    public Optional<ScanInventory> get() {
        return current.get();
    }
}
