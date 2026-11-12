package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

import java.util.HashSet;
import java.util.Set;

final class RepeatedIds implements IdPattern {

    @Override
    public Judgement judge(IdSample sample) {
        Set<String> seen = new HashSet<>();
        return sample.ids().stream().filter(id -> !seen.add(id)).findFirst()
                .map(repeated -> Judgement.weak("The server issued the same session ID, " + repeated
                        + ", to more than one new session."))
                .orElseGet(Judgement::sound);
    }
}
