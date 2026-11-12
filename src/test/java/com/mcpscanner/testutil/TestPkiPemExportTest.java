package com.mcpscanner.testutil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class TestPkiPemExportTest {

    @TempDir
    Path directory;

    @Test
    void exportsServerIdentityAndClientCaAsPemThatParsesBack() throws Exception {
        TestPki.ServerPemFiles pem = TestPki.generate(directory).exportServerPem();

        X509Certificate server = readCertificate(pem.certificate());
        X509Certificate ca = readCertificate(pem.clientCa());
        PrivateKey key = readPkcs8EcKey(pem.privateKey());

        assertThat(server.getSubjectX500Principal().getName()).isEqualTo("CN=localhost");
        assertThat(server.getIssuerX500Principal()).isEqualTo(ca.getSubjectX500Principal());
        server.verify(ca.getPublicKey());
        assertThat(key.getAlgorithm()).isEqualTo("EC");
        assertThat(Files.readString(pem.privateKey())).startsWith("-----BEGIN PRIVATE KEY-----\n");
    }

    private static X509Certificate readCertificate(Path file) throws Exception {
        assertThat(Files.readString(file)).startsWith("-----BEGIN CERTIFICATE-----\n");
        try (InputStream input = Files.newInputStream(file)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
    }

    private static PrivateKey readPkcs8EcKey(Path file) throws Exception {
        String base64 = Files.readString(file).replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }
}
