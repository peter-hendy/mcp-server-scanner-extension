package com.mcpscanner.checks.catalog.jwtvalidation;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

final class HmacSha256 {

    private static final String ALGORITHM = "HmacSHA256";
    private static final byte[] ZERO_PADDED_EMPTY_KEY = {0};

    private HmacSha256() {}

    static byte[] digest(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key.length == 0 ? ZERO_PADDED_EMPTY_KEY : key, ALGORITHM));
            return mac.doFinal(message);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
