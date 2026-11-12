package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.ui.widgets.LabeledForm;

public interface GrantCredentialForms {

    GrantCredentialForms NONE = new GrantCredentialForms() {
        @Override
        public void addTo(LabeledForm form) {
        }

        @Override
        public void show(IdentityAuthKind kind) {
        }

        @Override
        public boolean offers(IdentityAuthKind kind) {
            return !kind.isOAuthGrant();
        }
    };

    void addTo(LabeledForm form);

    void show(IdentityAuthKind kind);

    boolean offers(IdentityAuthKind kind);
}
