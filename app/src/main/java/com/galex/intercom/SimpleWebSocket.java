package com.galex.intercom;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Random;

public abstract class SimpleWebSocket {
    private final URI uri;
    private Socket socket;
    private Thread thread;
    private OutputStream out;

    public SimpleWebSocket(URI uri) { this.uri = uri; }

    public void connect() {
        thread = new Thread(() -> {
            try {
                socket = new Socket(uri.getHost(), uri.getPort() == -1 ? 80 : uri.getPort());
                out = socket.getOutputStream();
                InputStream in = socket.getInputStream();

                // Handshake
                byte[] nonce = new byte[16];
                new Random().nextBytes(nonce);
                String key = Base64.getEncoder().encodeToString(nonce);
                String path = uri.getPath().isEmpty() ? "/" : uri.getPath();
                String req = "GET " + path + " HTTP/1.1\r\n"
                        + "Host: " + uri.getHost() + ":" + (uri.getPort() == -1 ? 80 : uri.getPort()) + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: " + key + "\r\n"
                        + "Sec-WebSocket-Version: 13\r\n\r\n";
                out.write(req.getBytes());

                // Read HTTP response
                StringBuilder resp = new StringBuilder();
                int b, prev = 0;
                while ((b = in.read()) != -1) {
                    resp.append((char) b);
                    if (prev == '\r' && b == '\n' && resp.toString().endsWith("\r\n\r\n")) break;
                    prev = b;
                }

                // Read frames
                while (!Thread.interrupted() && !socket.isClosed()) {
                    int first = in.read();
                    if (first == -1) break;
                    int second = in.read();
                    if (second == -1) break;
                    int opcode = first & 0x0F;
                    long length = second & 0x7F;
                    if (length == 126) {
                        length = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
                    } else if (length == 127) {
                        length = 0;
                        for (int i = 0; i < 8; i++) length = (length << 8) | (in.read() & 0xFF);
                    }
                    byte[] data = new byte[(int) length];
                    int read = 0;
                    while (read < data.length) {
                        int r = in.read(data, read, data.length - read);
                        if (r == -1) break;
                        read += r;
                    }
                    if (opcode == 1) onMessage(new String(data));
                    else if (opcode == 8) { onClose(1000, "closed"); break; }
                }
            } catch (Exception e) {
                onError(e);
                onClose(1006, e.getMessage());
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void close() {
        try { if (socket != null) socket.close(); } catch (Exception e) {}
        if (thread != null) thread.interrupt();
    }

    public abstract void onMessage(String message);
    public abstract void onClose(int code, String reason);
    public abstract void onError(Exception e);
}
