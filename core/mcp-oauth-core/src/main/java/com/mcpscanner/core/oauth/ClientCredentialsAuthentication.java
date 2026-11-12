package com.mcpscanner.core.oauth;

public final class ClientCredentialsAuthentication {
    private final String method;
    private final String algorithm;
    private final String keyId;
    private final ClientAssertionSigner signer;

    private ClientCredentialsAuthentication(String method, String algorithm, String keyId, ClientAssertionSigner signer) {
        this.method = method;
        this.algorithm = algorithm;
        this.keyId = keyId;
        this.signer = signer;
    }
    public static ClientCredentialsAuthentication publicClient() {
        return new ClientCredentialsAuthentication("none", null, null, null);
    }
    public static ClientCredentialsAuthentication basic() {
        return new ClientCredentialsAuthentication("client_secret_basic", null, null, null);
    }
    public static ClientCredentialsAuthentication post() {
        return new ClientCredentialsAuthentication("client_secret_post", null, null, null);
    }
    public static ClientCredentialsAuthentication privateKeyJwt(String algorithm, String keyId, ClientAssertionSigner signer) {
        return new ClientCredentialsAuthentication("private_key_jwt", algorithm, keyId, signer);
    }
    public String method() { return method; }
    String algorithm() { return algorithm; }
    String keyId() { return keyId; }
    ClientAssertionSigner signer() { return signer; }
    @Override public String toString() { return "ClientCredentialsAuthentication[redacted]"; }
}
