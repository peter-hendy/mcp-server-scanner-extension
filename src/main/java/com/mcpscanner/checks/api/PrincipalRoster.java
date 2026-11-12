package com.mcpscanner.checks.api;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public interface PrincipalRoster {

    Principal primary();

    List<Principal> compared();

    default List<Principal> all() {
        return Stream.concat(Stream.of(primary()), compared().stream()).toList();
    }

    static PrincipalRoster of(Principal primary, List<Principal> compared) {
        return new FixedPrincipalRoster(primary, compared);
    }

    static PrincipalRoster empty() {
        return of(Principal.primary(Map::of), List.of());
    }
}
