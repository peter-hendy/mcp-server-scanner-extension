package com.mcpscanner.ui;

import com.mcpscanner.auth.grants.ClientAuthenticationProviderRegistry;
import com.mcpscanner.auth.grants.EnterpriseIdentityProviderRegistry;
import com.mcpscanner.auth.grants.OptionalGrantSettings;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.ui.connect.OAuthGrantKinds;
import com.mcpscanner.ui.identities.GrantCredentialForms;
import com.mcpscanner.ui.widgets.LabeledForm;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

final class OptionalGrantForms implements GrantCredentialForms {

    private final Map<OptionalGrantSettings.Profile, OptionalGrantFields> fields =
            new EnumMap<>(OptionalGrantSettings.Profile.class);

    OptionalGrantForms(ClientAuthenticationProviderRegistry signers,
                       EnterpriseIdentityProviderRegistry identityProviders) {
        for (OptionalGrantSettings.Profile profile : OptionalGrantSettings.Profile.values()) {
            fields.put(profile, new OptionalGrantFields(profile, signers, identityProviders));
        }
    }

    @Override
    public void addTo(LabeledForm form) {
        fields.values().forEach(grant -> grant.addTo(form));
    }

    @Override
    public void show(IdentityAuthKind kind) {
        Optional<OptionalGrantSettings.Profile> shown = OAuthGrantKinds.profileOf(kind);
        fields.forEach((profile, grant) -> grant.setShown(shown.filter(profile::equals).isPresent()));
    }

    @Override
    public boolean offers(IdentityAuthKind kind) {
        return OAuthGrantKinds.profileOf(kind).map(fields::get).map(OptionalGrantFields::available).orElse(true);
    }

    Optional<String> blocker(OptionalGrantSettings.Profile profile) {
        return fields.get(profile).blocker();
    }

    OptionalGrantSettings snapshot(OptionalGrantSettings.Profile profile) {
        return fields.get(profile).snapshot();
    }

    void setChangeListener(Runnable listener) {
        fields.values().forEach(grant -> grant.setChangeListener(listener));
    }

    void clear() {
        fields.values().forEach(OptionalGrantFields::clear);
    }

    void clearSecrets() {
        fields.values().forEach(OptionalGrantFields::clearSecret);
    }

    OptionalGrantFields fieldsFor(OptionalGrantSettings.Profile profile) {
        return fields.get(profile);
    }
}
