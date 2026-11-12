package com.mcpscanner.testutil;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Test-time PKI generated with the JDK {@code keytool}: a CA, a CA-signed server certificate for
 * 127.0.0.1/localhost, a CA-signed client certificate and a self-signed "untrusted" client
 * certificate. Nothing is committed; every keystore lives in the supplied temporary directory.
 */
public final class TestPki {

    private static final String PASSWORD = "changeit";
    private static final String CA = "ca";
    private static final String SERVER = "server";
    private static final String CLIENT = "client";
    private static final String UNTRUSTED_CLIENT = "untrusted-client";
    private static final long KEYTOOL_TIMEOUT_SECONDS = 30;
    private static final String[] HANDSHAKE_REJECTING_PROTOCOLS = {"TLSv1.2"};

    private final Path directory;
    private final X509Certificate caCertificate;
    private final KeyStore serverIdentity;
    private final KeyStore clientIdentity;
    private final KeyStore untrustedClientIdentity;

    private TestPki(Path directory) throws IOException, GeneralSecurityException {
        this.directory = directory;
        generateSelfSigned(CA, "CN=Test CA", "-ext", "bc:c");
        this.caCertificate = (X509Certificate) load(CA).getCertificate(CA);
        this.serverIdentity = issue(SERVER, "CN=localhost", "san=ip:127.0.0.1,dns:localhost", "eku=serverAuth");
        this.clientIdentity = issue(CLIENT, "CN=Test Client", "eku=clientAuth");
        generateSelfSigned(UNTRUSTED_CLIENT, "CN=Untrusted Client");
        this.untrustedClientIdentity = load(UNTRUSTED_CLIENT);
    }

    public static TestPki generate(Path directory) throws IOException, GeneralSecurityException {
        return new TestPki(directory);
    }

    public KeyStore clientIdentity() {
        return clientIdentity;
    }

    public KeyStore untrustedClientIdentity() {
        return untrustedClientIdentity;
    }

    public record ServerPemFiles(Path certificate, Path privateKey, Path clientCa) {
    }

    public ServerPemFiles exportServerPem() throws IOException, GeneralSecurityException {
        X509Certificate serverCertificate = (X509Certificate) serverIdentity.getCertificate(SERVER);
        PrivateKey serverKey = (PrivateKey) serverIdentity.getKey(SERVER, PASSWORD.toCharArray());
        return new ServerPemFiles(
                writePem("server-cert.pem", Pem.certificate(serverCertificate)),
                writePem("server-key.pem", Pem.pkcs8PrivateKey(serverKey)),
                writePem("ca-cert.pem", Pem.certificate(caCertificate)));
    }

    private Path writePem(String name, String pem) throws IOException {
        return Files.writeString(directory.resolve(name), pem);
    }

    public HttpsConfigurator serverRequiringClientCertificate() throws GeneralSecurityException {
        return serverRequiringClientCertificate(Optional.of(HANDSHAKE_REJECTING_PROTOCOLS));
    }

    public HttpsConfigurator serverRequiringClientCertificateOnDefaultTls() throws GeneralSecurityException {
        return serverRequiringClientCertificate(Optional.empty());
    }

    private HttpsConfigurator serverRequiringClientCertificate(Optional<String[]> pinnedProtocols)
            throws GeneralSecurityException {
        return new HttpsConfigurator(sslContext(keyManagers(Optional.of(serverIdentity)), caTrustManagers())) {
            @Override
            public void configure(HttpsParameters parameters) {
                SSLParameters ssl = getSSLContext().getDefaultSSLParameters();
                ssl.setNeedClientAuth(true);
                pinnedProtocols.ifPresent(ssl::setProtocols);
                parameters.setSSLParameters(ssl);
            }
        };
    }

    public SSLContext clientTrustingCa(Optional<KeyStore> identity) throws GeneralSecurityException {
        return sslContext(keyManagers(identity), caTrustManagers());
    }

    public static SSLContext clientTrustingAnyServer(Optional<KeyStore> identity) throws GeneralSecurityException {
        return sslContext(keyManagers(identity), new TrustManager[] {new AnyServerTrustManager()});
    }

    private KeyStore issue(String alias, String subject, String... extensions)
            throws IOException, GeneralSecurityException {
        generateSelfSigned(alias, subject);
        keytool("-certreq", "-alias", alias, "-keystore", keystore(alias), "-file", file(alias + ".csr"));
        List<String> gencert = new ArrayList<>(List.of("-gencert", "-rfc", "-alias", CA, "-keystore", keystore(CA),
                "-infile", file(alias + ".csr"), "-outfile", file(alias + ".crt"), "-validity", "2"));
        for (String extension : extensions) {
            gencert.addAll(List.of("-ext", extension));
        }
        keytool(gencert.toArray(String[]::new));
        return withChain(load(alias), alias, readCertificate(alias + ".crt"));
    }

    private KeyStore withChain(KeyStore store, String alias, Certificate leaf) throws GeneralSecurityException {
        Key key = store.getKey(alias, PASSWORD.toCharArray());
        store.setKeyEntry(alias, key, PASSWORD.toCharArray(), new Certificate[] {leaf, caCertificate});
        return store;
    }

    private void generateSelfSigned(String alias, String subject, String... extraArgs)
            throws IOException {
        List<String> args = new ArrayList<>(List.of("-genkeypair", "-alias", alias, "-keyalg", "EC",
                "-groupname", "secp256r1", "-dname", subject, "-validity", "2", "-keystore", keystore(alias)));
        args.addAll(List.of(extraArgs));
        keytool(args.toArray(String[]::new));
    }

    private void keytool(String... args) throws IOException {
        List<String> command = new ArrayList<>(List.of(keytoolPath(), "-noprompt", "-storetype", "PKCS12",
                "-storepass", PASSWORD, "-keypass", PASSWORD));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        awaitSuccess(process, command, output);
    }

    private static void awaitSuccess(Process process, List<String> command, String output) throws IOException {
        try {
            if (!process.waitFor(KEYTOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS) || process.exitValue() != 0) {
                process.destroyForcibly();
                throw new IOException("keytool failed: " + command + "\n" + output);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for keytool", interrupted);
        }
    }

    private static String keytoolPath() {
        return Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
    }

    private KeyStore load(String alias) throws IOException, GeneralSecurityException {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(Path.of(keystore(alias)))) {
            store.load(input, PASSWORD.toCharArray());
        }
        return store;
    }

    private Certificate readCertificate(String name) throws IOException, GeneralSecurityException {
        try (InputStream input = Files.newInputStream(Path.of(file(name)))) {
            return CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
    }

    private String keystore(String alias) {
        return file(alias + ".p12");
    }

    private String file(String name) {
        return directory.resolve(name).toString();
    }

    private TrustManager[] caTrustManagers() throws GeneralSecurityException {
        KeyStore trusted = KeyStore.getInstance("PKCS12");
        try {
            trusted.load(null, null);
        } catch (IOException impossibleForEmptyStore) {
            throw new GeneralSecurityException(impossibleForEmptyStore);
        }
        trusted.setCertificateEntry(CA, caCertificate);
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(trusted);
        return factory.getTrustManagers();
    }

    private static KeyManager[] keyManagers(Optional<KeyStore> identity) throws GeneralSecurityException {
        if (identity.isEmpty()) {
            return new KeyManager[0];
        }
        KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(identity.get(), PASSWORD.toCharArray());
        return factory.getKeyManagers();
    }

    private static SSLContext sslContext(KeyManager[] keys, TrustManager[] trust) throws GeneralSecurityException {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys, trust, null);
        return context;
    }

    private static final class AnyServerTrustManager extends X509ExtendedTrustManager {
        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            throw new UnsupportedOperationException("Client-side trust manager");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
            throw new UnsupportedOperationException("Client-side trust manager");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            throw new UnsupportedOperationException("Client-side trust manager");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
