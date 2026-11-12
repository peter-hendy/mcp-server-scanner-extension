package com.mcpscanner.checks.catalog.sessionbinding;

import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.crossidentity.AnonymousReply;
import com.mcpscanner.checks.api.crossidentity.OperationReply;
import com.mcpscanner.checks.api.crossidentity.OwnerBaseline;
import com.mcpscanner.checks.api.crossidentity.ProbeKit;
import com.mcpscanner.checks.api.crossidentity.ReplaySkips;

import java.util.List;

final class SessionBindingRun {

    private final ProbeKit kit;
    private final List<Principal> claimed;

    SessionBindingRun(ProbeKit kit, List<Principal> claimed) {
        this.kit = kit;
        this.claimed = List.copyOf(claimed);
    }

    List<IdentityOutcome> run() {
        return new OwnerBaseline(kit).acquire().map(this::compare).orElseGet(this::unsuitableSeed);
    }

    private List<IdentityOutcome> compare(OperationReply owner) {
        AnonymousReply anonymous = AnonymousReply.fetch(kit);
        if (!anonymous.reply().answered()) {
            kit.log().record("anonymous_unanswered");
            kit.skippedUnlessStopped(ReplaySkips.ANONYMOUS_UNANSWERED);
            return unsuitableSeed();
        }
        if (owner.sameResult(anonymous.reply())) {
            kit.log().record("anonymous_operation_success");
            kit.log().notRun(ReplaySkips.ANONYMOUS_GETS_OWNER_RESULT);
            return unsuitableSeed();
        }
        return claimed.stream()
                .map(principal -> new IdentityProbe(kit.forPrincipal(principal), principal, owner, anonymous).run())
                .toList();
    }

    private List<IdentityOutcome> unsuitableSeed() {
        Verdict verdict = kit.transport().stopped() ? Verdict.INCONCLUSIVE : Verdict.UNSUITABLE_SEED;
        return claimed.stream().map(principal -> IdentityOutcome.unprobed(principal, verdict)).toList();
    }
}
