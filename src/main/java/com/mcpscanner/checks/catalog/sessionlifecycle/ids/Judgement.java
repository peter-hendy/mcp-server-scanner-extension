package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

import java.util.Optional;

record Judgement(Verdict verdict, Optional<String> weakness) {

    enum Verdict { WEAK, SOUND, UNDECIDED }

    static Judgement weak(String weakness) {
        return new Judgement(Verdict.WEAK, Optional.of(weakness));
    }

    static Judgement sound() {
        return new Judgement(Verdict.SOUND, Optional.empty());
    }

    static Judgement undecided() {
        return new Judgement(Verdict.UNDECIDED, Optional.empty());
    }

    boolean isUndecided() {
        return verdict == Verdict.UNDECIDED;
    }
}
