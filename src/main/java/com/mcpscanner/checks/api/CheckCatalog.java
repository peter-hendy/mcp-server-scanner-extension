package com.mcpscanner.checks.api;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

public final class CheckCatalog {

    private final Map<String, McpCheck> checksById;

    private CheckCatalog(Map<String, McpCheck> checksById) {
        this.checksById = checksById;
    }

    public static CheckCatalog discover(CheckServices services) {
        return of(registeredFactories(), services);
    }

    public static CheckCatalog of(List<? extends CheckFactory> factories, CheckServices services) {
        Objects.requireNonNull(services, "services must not be null");
        Map<String, McpCheck> checksById = new LinkedHashMap<>();
        factories.stream()
                .map(factory -> factory.create(services))
                .sorted(Comparator.comparing(check -> check.descriptor().id()))
                .forEach(check -> addUnique(checksById, check));
        return new CheckCatalog(checksById);
    }

    public List<McpCheck> all() {
        return List.copyOf(checksById.values());
    }

    public Optional<McpCheck> find(String id) {
        return Optional.ofNullable(checksById.get(id));
    }

    public <T> Optional<T> find(String id, Class<T> capability) {
        return find(id).filter(capability::isInstance).map(capability::cast);
    }

    private static List<CheckFactory> registeredFactories() {
        return ServiceLoader.load(CheckFactory.class, CheckFactory.class.getClassLoader()).stream()
                .map(ServiceLoader.Provider::get)
                .toList();
    }

    private static void addUnique(Map<String, McpCheck> checksById, McpCheck check) {
        ImplementedKind.requireDeclaredKindMatches(check);
        String id = check.descriptor().id();
        if (checksById.putIfAbsent(id, check) != null) {
            throw new IllegalStateException("Duplicate check id registered: " + id);
        }
    }
}
