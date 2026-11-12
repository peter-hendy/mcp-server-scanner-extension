package com.mcpscanner.ui.access;

import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.identity.AnonymousComparison;

import java.util.List;
import java.util.stream.Collectors;

final class CheckAccessScope {

    private static final int NAMED_USERS = 3;

    private CheckAccessScope() {
    }

    static String describe(int items, Principal primary, List<Principal> compared) {
        return items + (items == 1 ? " item" : " items") + " · " + primary.name() + " vs "
                + AnonymousComparison.alongside(comparedNames(compared));
    }

    private static String comparedNames(List<Principal> compared) {
        String named = compared.stream().limit(NAMED_USERS).map(Principal::name).collect(Collectors.joining(", "));
        int unnamed = compared.size() - NAMED_USERS;
        return unnamed > 0 ? named + " +" + unnamed + " more" : named;
    }
}
