package com.mcpscanner.checks.catalog.commandinjection;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

final class InBandShellProbe {

    private static final String TECHNIQUE = "shell arithmetic expansion";

    private final CanarySource confirmations;

    InBandShellProbe(CanarySource confirmations) {
        this.confirmations = confirmations;
    }

    ProbeOutcome probe(ProbeField field, InjectionReply baseline, ArithmeticCanary canary) {
        for (ShellBreakout breakout : field.breakouts()) {
            ProbeOutcome result = probe(field, baseline, canary, breakout);
            if (result.conclusive()) {
                return result;
            }
        }
        return ProbeOutcome.inconclusive();
    }

    private ProbeOutcome probe(ProbeField field, InjectionReply baseline, ArithmeticCanary canary, ShellBreakout breakout) {
        String computed = canary.result();
        InjectionReply injected = field.send(breakout.wrap(field.original(), canary.command()));
        if (!injected.answered() || !injected.contains(computed)) {
            return ProbeOutcome.inconclusive();
        }
        InjectionReply direct = field.send(canary.command());
        if (computes(direct, canary)) {
            return found(FindingKind.INTENDED_COMMAND, field, List.of(computed), baseline, injected, direct);
        }
        InjectionReply matched = field.sendMatchedControl();
        if (matched.successful() && !matched.contains(computed)) {
            return confirmed(FindingKind.SHELL_COMPUTED_RESULT, field, breakout, computed, baseline, injected, matched);
        }
        if (!matched.rejected() || matched.contains(computed)) {
            return ProbeOutcome.inconclusive();
        }
        InjectionReply quoted = field.send(breakout.wrap(field.original(), canary.quotedCommand()));
        if (quoted.answered() && !quoted.contains(computed) && quoted.contains(canary.quotedLiteral())) {
            return confirmed(FindingKind.SHELL_COMPUTED_RESULT_QUOTED_CONTROL, field, breakout, computed, baseline,
                    injected, matched, quoted);
        }
        return ProbeOutcome.inconclusive();
    }

    private ProbeOutcome confirmed(FindingKind kind, ProbeField field, ShellBreakout breakout, String computed,
                                   InjectionReply... controls) {
        ArithmeticCanary fresh = confirmations.next();
        InjectionReply repeated = field.send(breakout.wrap(field.original(), fresh.command()));
        if (!computes(repeated, fresh)) {
            return ProbeOutcome.inconclusive();
        }
        InjectionReply repeatedDirect = field.send(fresh.command());
        FindingKind confirmedKind = computes(repeatedDirect, fresh) ? FindingKind.INTENDED_COMMAND : kind;
        InjectionReply[] evidence = Stream.concat(Arrays.stream(controls), Stream.of(repeated, repeatedDirect))
                .toArray(InjectionReply[]::new);
        return found(confirmedKind, field, List.of(computed, fresh.result()), evidence);
    }

    private static boolean computes(InjectionReply reply, ArithmeticCanary canary) {
        return reply.answered() && reply.contains(canary.result());
    }

    private static ProbeOutcome found(FindingKind kind, ProbeField field, List<String> proofs,
                                      InjectionReply... replies) {
        return ProbeOutcome.found(Finding.proving(kind, field.subject(), TECHNIQUE, proofs, replies));
    }
}
