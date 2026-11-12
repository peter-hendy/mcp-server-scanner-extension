package com.mcpscanner.checks.catalog.jwtvalidation;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;

final class RandomRsaSignature implements ForgedSignature {

    private static final int KEY_BITS = 2048;

    private final ObjectNode header;
    private final KeyPair keyPair;

    RandomRsaSignature(CompactJwt captured) {
        this.header = ForgedHeader.from(captured, "RS256");
        this.keyPair = generateKeyPair();
    }

    @Override
    public String description() {
        return "an RS256 token signed with a freshly generated RSA key the server has never seen";
    }

    @Override
    public String sign(ObjectNode claims) {
        UnsignedJwt unsigned = new UnsignedJwt(header, claims);
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(keyPair.getPrivate());
            signer.update(unsigned.signingInputBytes());
            return unsigned.withSignature(signer.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RS256 signing unavailable", e);
        }
    }

    PublicKey publicKey() {
        return keyPair.getPublic();
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(KEY_BITS);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA key generation unavailable", e);
        }
    }
}
