package com.mcpscanner.auth.oauth;

import com.mcpscanner.config.ClientIdentity;
import com.mcpscanner.config.ClientIdentityField;
import com.nimbusds.oauth2.sdk.client.ClientMetadata;
import com.nimbusds.oauth2.sdk.id.SoftwareID;
import com.nimbusds.oauth2.sdk.id.SoftwareVersion;

import java.net.URI;
import java.util.List;

final class ClientIdentityRegistration {

    private ClientIdentityRegistration() {
    }

    static void applyTo(ClientMetadata metadata, ClientIdentity identity) {
        metadata.setName(identity.value(ClientIdentityField.OAUTH_CLIENT_NAME));
        ifPresent(identity, ClientIdentityField.CLIENT_URI, value -> metadata.setURI(URI.create(value)));
        ifPresent(identity, ClientIdentityField.LOGO_URI, value -> metadata.setLogoURI(URI.create(value)));
        ifPresent(identity, ClientIdentityField.TOS_URI, value -> metadata.setTermsOfServiceURI(URI.create(value)));
        ifPresent(identity, ClientIdentityField.POLICY_URI, value -> metadata.setPolicyURI(URI.create(value)));
        ifPresent(identity, ClientIdentityField.SOFTWARE_ID, value -> metadata.setSoftwareID(new SoftwareID(value)));
        ifPresent(identity, ClientIdentityField.SOFTWARE_VERSION,
                value -> metadata.setSoftwareVersion(new SoftwareVersion(value)));
        List<String> contacts = identity.contacts();
        if (!contacts.isEmpty()) {
            metadata.setEmailContacts(contacts);
        }
    }

    private static void ifPresent(ClientIdentity identity, ClientIdentityField field,
                                  java.util.function.Consumer<String> apply) {
        String value = identity.value(field);
        if (!value.isEmpty()) {
            apply.accept(value);
        }
    }
}
