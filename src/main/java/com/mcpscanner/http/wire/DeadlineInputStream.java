package com.mcpscanner.http.wire;

import java.io.FilterInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Instant;

final class DeadlineInputStream extends FilterInputStream {

    private final Socket socket;
    private final Instant deadline;

    DeadlineInputStream(Socket socket, Instant deadline) throws IOException {
        super(socket.getInputStream());
        this.socket = socket;
        this.deadline = deadline;
    }

    @Override
    public int read() throws IOException {
        armRemainingTime();
        return super.read();
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        armRemainingTime();
        return super.read(buffer, offset, length);
    }

    private void armRemainingTime() throws IOException {
        long remainingMillis = deadline.toEpochMilli() - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            throw new SocketTimeoutException("read deadline passed");
        }
        socket.setSoTimeout((int) Math.min(Integer.MAX_VALUE, remainingMillis));
    }
}
