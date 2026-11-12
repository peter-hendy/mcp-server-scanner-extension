package com.mcpscanner.core.oauth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;

final class ClientAssertionFactory {
    String create(RegisteredClient client, java.net.URI endpoint, ClientCredentialsAuthentication auth, Clock clock) {
        String header = "{\"alg\":" + quote(auth.algorithm()) + ",\"typ\":\"JWT\""
                + (auth.keyId() == null ? "" : ",\"kid\":" + quote(auth.keyId())) + "}";
        long now = clock.instant().getEpochSecond();
        String claims = "{\"iss\":" + quote(client.clientId()) + ",\"sub\":" + quote(client.clientId())
                + ",\"aud\":" + quote(endpoint.toString())
                + ",\"iat\":" + now + ",\"exp\":" + Math.addExact(now, 60) + ",\"jti\":" + quote(UUID.randomUUID().toString()) + "}";
        String input = encode(header.getBytes(StandardCharsets.UTF_8)) + "." + encode(claims.getBytes(StandardCharsets.UTF_8));
        byte[] signature = auth.signer().sign(auth.algorithm(), input.getBytes(StandardCharsets.US_ASCII));
        if (signature == null || signature.length == 0 || signature.length > 16384) throw new IllegalArgumentException();
        return input + "." + encode(signature);
    }
    private String encode(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private String quote(String value) {
        var result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"' || character == '\\') result.append('\\');
            if (character < 0x20) result.append(String.format("\\u%04x", (int) character));
            else result.append(character);
        }
        return result.append('"').toString();
    }
}
