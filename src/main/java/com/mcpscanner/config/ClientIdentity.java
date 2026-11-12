package com.mcpscanner.config;

import com.mcpscanner.core.protocol.ClientIcon;
import com.mcpscanner.core.protocol.ClientInfo;
import com.mcpscanner.core.protocol.ClientProfile;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class ClientIdentity {

    private static final ClientIdentity DEFAULTS = from(field -> "");

    private final Map<ClientIdentityField, String> values;

    private ClientIdentity(Map<ClientIdentityField, String> values) {
        this.values = values;
    }

    public static ClientIdentity defaults() {
        return DEFAULTS;
    }

    public static ClientIdentity from(Function<ClientIdentityField, String> source) {
        Map<ClientIdentityField, String> normalized = new EnumMap<>(ClientIdentityField.class);
        for (ClientIdentityField field : ClientIdentityField.values()) {
            String value = field.normalize(source.apply(field));
            field.validate(value);
            normalized.put(field, value);
        }
        return new ClientIdentity(normalized);
    }

    public String value(ClientIdentityField field) {
        return values.get(field);
    }

    public ClientProfile profile() {
        ClientInfo info = new ClientInfo(
                value(ClientIdentityField.NAME), value(ClientIdentityField.VERSION),
                optional(ClientIdentityField.TITLE), optional(ClientIdentityField.DESCRIPTION),
                optional(ClientIdentityField.WEBSITE_URL), icons());
        return ClientProfile.of(info);
    }

    public List<String> contacts() {
        return Arrays.stream(value(ClientIdentityField.CONTACT).split(","))
                .map(String::trim).filter(contact -> !contact.isEmpty()).toList();
    }

    private List<ClientIcon> icons() {
        String icon = value(ClientIdentityField.ICON_URL);
        return icon.isEmpty() ? null : List.of(ClientIcon.of(icon));
    }

    private String optional(ClientIdentityField field) {
        String value = value(field);
        return value.isEmpty() ? null : value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ClientIdentity that && values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }
}
