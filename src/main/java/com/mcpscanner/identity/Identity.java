package com.mcpscanner.identity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record Identity(
        String id,
        String name,
        IdentityRole role,
        IdentityAuthKind authKind,
        boolean compared,
        Optional<String> privateMarker,
        OwnedValues ownedValues,
        IdentityOAuthSettings oauth,
        List<String> headerNames
) {
    public Identity {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(authKind, "authKind must not be null");
        privateMarker = privateMarker == null ? Optional.empty() : privateMarker;
        ownedValues = ownedValues == null ? OwnedValues.NONE : ownedValues;
        oauth = oauth == null ? IdentityOAuthSettings.none() : oauth;
        headerNames = headerNames == null ? List.of() : List.copyOf(headerNames);
    }

    public static Identity create(String name, IdentityRole role, IdentityAuthKind authKind) {
        return new Identity(newId(), name, role, authKind, true, Optional.empty(), OwnedValues.NONE,
                IdentityOAuthSettings.none(), List.of());
    }

    public Identity copyNamed(String copyName) {
        return edit().id(newId()).name(copyName).build();
    }

    public Identity withName(String newName) {
        return edit().name(newName).build();
    }

    public Identity withRole(IdentityRole newRole) {
        return edit().role(newRole).build();
    }

    public Identity withAuthKind(IdentityAuthKind newKind) {
        return edit().authKind(newKind).build();
    }

    public Identity withCompared(boolean isCompared) {
        return edit().compared(isCompared).build();
    }

    public Identity withPrivateMarker(String marker) {
        return edit().privateMarker(Optional.ofNullable(marker)).build();
    }

    public Identity withOwnedValues(OwnedValues values) {
        return edit().ownedValues(values).build();
    }

    public Identity withOAuth(IdentityOAuthSettings settings) {
        return edit().oauth(settings).build();
    }

    public Identity withHeaderNames(List<String> names) {
        return edit().headerNames(names).build();
    }

    private Editor edit() {
        return new Editor(this);
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static final class Editor {
        private String id;
        private String name;
        private IdentityRole role;
        private IdentityAuthKind authKind;
        private boolean compared;
        private Optional<String> privateMarker;
        private OwnedValues ownedValues;
        private IdentityOAuthSettings oauth;
        private List<String> headerNames;

        private Editor(Identity source) {
            id = source.id;
            name = source.name;
            role = source.role;
            authKind = source.authKind;
            compared = source.compared;
            privateMarker = source.privateMarker;
            ownedValues = source.ownedValues;
            oauth = source.oauth;
            headerNames = source.headerNames;
        }

        Editor id(String value) {
            id = value;
            return this;
        }

        Editor name(String value) {
            name = value;
            return this;
        }

        Editor role(IdentityRole value) {
            role = value;
            return this;
        }

        Editor authKind(IdentityAuthKind value) {
            authKind = value;
            return this;
        }

        Editor compared(boolean value) {
            compared = value;
            return this;
        }

        Editor privateMarker(Optional<String> value) {
            privateMarker = value;
            return this;
        }

        Editor ownedValues(OwnedValues value) {
            ownedValues = value;
            return this;
        }

        Editor oauth(IdentityOAuthSettings value) {
            oauth = value;
            return this;
        }

        Editor headerNames(List<String> value) {
            headerNames = value;
            return this;
        }

        Identity build() {
            return new Identity(id, name, role, authKind, compared, privateMarker, ownedValues, oauth, headerNames);
        }
    }
}
