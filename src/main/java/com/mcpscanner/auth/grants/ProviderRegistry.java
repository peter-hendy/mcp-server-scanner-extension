package com.mcpscanner.auth.grants;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class ProviderRegistry<T> {

    record Rules<T>(Function<T, String> idOf,
                    Predicate<T> isComplete,
                    Supplier<RuntimeException> invalidRegistration,
                    Supplier<RuntimeException> loadFailure,
                    Supplier<RuntimeException> missingProvider) {}

    private final Map<String, T> providers;
    private final Rules<T> rules;

    ProviderRegistry(List<? extends T> registrations, Rules<T> rules) {
        this.rules = rules;
        var registered = new LinkedHashMap<String, T>();
        for (T registration : registrations) {
            if (!isValid(registration) || registered.putIfAbsent(rules.idOf().apply(registration), registration) != null) {
                throw rules.invalidRegistration().get();
            }
        }
        this.providers = Map.copyOf(registered);
    }

    static <T> ProviderRegistry<T> load(Class<T> spi, ClassLoader loader, Rules<T> rules) {
        try {
            List<T> registrations = ServiceLoader.load(spi, Objects.requireNonNull(loader)).stream()
                    .map(ServiceLoader.Provider::get)
                    .toList();
            return new ProviderRegistry<>(registrations, rules);
        } catch (ServiceConfigurationError | RuntimeException failure) {
            throw rules.loadFailure().get();
        }
    }

    List<String> ids() {
        return providers.keySet().stream().sorted().toList();
    }

    T require(String id) {
        T provider = id == null ? null : providers.get(id);
        if (provider == null) {
            throw rules.missingProvider().get();
        }
        return provider;
    }

    private boolean isValid(T registration) {
        if (registration == null) {
            return false;
        }
        String id = rules.idOf().apply(registration);
        return id != null && !id.isBlank() && rules.isComplete().test(registration);
    }
}
