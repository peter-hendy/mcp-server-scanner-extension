package com.mcpscanner.http.wire;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

final class RecordingSslSocketFactory extends SSLSocketFactory {

    private final SSLSocketFactory delegate;
    private SSLSocket lastSocket;

    RecordingSslSocketFactory(SSLSocketFactory delegate) {
        this.delegate = delegate;
    }

    SSLSocket lastSocket() {
        return lastSocket;
    }

    @Override
    public Socket createSocket(Socket plain, String host, int port, boolean autoClose) throws IOException {
        return record(delegate.createSocket(plain, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return record(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return record(delegate.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return record(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
            throws IOException {
        return record(delegate.createSocket(address, port, localAddress, localPort));
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    private Socket record(Socket socket) {
        lastSocket = (SSLSocket) socket;
        return socket;
    }
}
