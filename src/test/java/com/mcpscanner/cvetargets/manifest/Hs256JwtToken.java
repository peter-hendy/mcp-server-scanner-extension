package com.mcpscanner.cvetargets.manifest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.Base64;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record Hs256JwtToken(String secret, Map<String, Object> claims, long ttlSeconds)
        implements TokenSource {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final String ALGORITHM = "HmacSHA256";

    public Hs256JwtToken {
        claims = claims == null ? Map.of() : Map.copyOf(claims);
    }

    @Override
    public String token() {
        return token(Clock.systemUTC());
    }

    @Override
    public List<String> violations() {
        List<String> violations = new ArrayList<>();
        if (secret == null || secret.isBlank()) {
            violations.add("auth.token.secret is required");
        }
        if (ttlSeconds <= 0) {
            violations.add("auth.token.ttlSeconds must be positive");
        }
        return violations;
    }

    public String token(Clock clock) {
        String signingInput = segment(Map.of("alg", "HS256", "typ", "JWT")) + "." + segment(timedClaims(clock));
        return signingInput + "." + URL_ENCODER.encodeToString(sign(signingInput));
    }

    private Map<String, Object> timedClaims(Clock clock) {
        long now = clock.instant().getEpochSecond();
        Map<String, Object> timed = new LinkedHashMap<>(claims);
        timed.put("iat", now);
        timed.put("exp", now + ttlSeconds);
        return timed;
    }

    private static String segment(Map<String, Object> value) {
        try {
            return URL_ENCODER.encodeToString(JSON.writeValueAsBytes(value));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise JWT segment", e);
        }
    }

    private byte[] sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot sign JWT", e);
        }
    }
}
