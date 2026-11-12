package com.mcpscanner.testutil;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;

final class Pem {

    private static final Base64.Encoder MIME = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII));

    private Pem() {
    }

    static String certificate(X509Certificate certificate) throws CertificateEncodingException {
        return block("CERTIFICATE", certificate.getEncoded());
    }

    static String pkcs8PrivateKey(PrivateKey key) {
        return block("PRIVATE KEY", key.getEncoded());
    }

    private static String block(String label, byte[] der) {
        return "-----BEGIN " + label + "-----\n" + MIME.encodeToString(der) + "\n-----END " + label + "-----\n";
    }
}
