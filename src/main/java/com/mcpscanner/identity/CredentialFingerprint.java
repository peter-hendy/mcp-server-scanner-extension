package com.mcpscanner.identity;

import com.mcpscanner.auth.AuthStrategy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

public record CredentialFingerprint(String identityId, String digest) {

    private static final String FIELD_SEPARATOR = "\n";

    public CredentialFingerprint {
        Objects.requireNonNull(identityId, "identityId");
        Objects.requireNonNull(digest, "digest");
    }

    public static CredentialFingerprint of(Identity user, Optional<AuthStrategy> credential) {
        return new CredentialFingerprint(user.id(), sha256(String.join(FIELD_SEPARATOR,
                user.authKind().name(),
                String.join(",", user.headerNames()),
                user.oauth().issuer(),
                user.oauth().clientId(),
                String.join(" ", user.oauth().scopes()),
                credential.map(CredentialFingerprint::credentialMaterial).orElse(""))));
    }

    private static String credentialMaterial(AuthStrategy credential) {
        if (credential.supportsRefresh()) {
            return "session " + System.identityHashCode(credential);
        }
        return new TreeMap<>(credential.headers()).entrySet().stream()
                .map(CredentialFingerprint::headerLine)
                .collect(Collectors.joining(FIELD_SEPARATOR));
    }

    private static String headerLine(Map.Entry<String, String> header) {
        return header.getKey() + ": " + header.getValue();
    }

    private static String sha256(String material) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }
}
