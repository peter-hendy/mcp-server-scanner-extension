package com.mcpscanner.ui;

import com.mcpscanner.identity.IdentityRegistry;
import com.mcpscanner.ui.widgets.Edt;

import java.util.concurrent.atomic.AtomicReference;

final class GrantFieldsFollowPrimary implements Runnable {

    private final IdentityRegistry registry;
    private final OptionalGrantForms forms;
    private final AtomicReference<String> primaryId;

    private GrantFieldsFollowPrimary(IdentityRegistry registry, OptionalGrantForms forms) {
        this.registry = registry;
        this.forms = forms;
        this.primaryId = new AtomicReference<>(registry.primaryUser().id());
    }

    static Runnable watch(IdentityRegistry registry, OptionalGrantForms forms) {
        GrantFieldsFollowPrimary watcher = new GrantFieldsFollowPrimary(registry, forms);
        registry.addListener(watcher);
        return () -> registry.removeListener(watcher);
    }

    @Override
    public void run() {
        String current = registry.primaryUser().id();
        if (!current.equals(primaryId.getAndSet(current))) {
            Edt.run(forms::clear);
        }
    }
}
