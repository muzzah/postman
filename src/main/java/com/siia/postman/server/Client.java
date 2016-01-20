package com.siia.postman.server;

import com.osiyent.sia.commons.core.log.Logcat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import static com.osiyent.sia.commons.core.log.Logcat.v;

public class Client {
    private static final String TAG = Logcat.getTag();
    private static final int BUFFER_SIZE = 4096;

    private final SelectionKey clientKey;

    public Client(SelectionKey clientKey) {
        this.clientKey = clientKey;
    }

    public ByteBuffer read() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        int bytesRead = getSocketChannel().read(buffer);
        if (bytesRead == -1) {
            throw new IOException("Invalid bytes read from channel");
        }
        v(TAG, "Read %d bytes", bytesRead);
        buffer.flip();
        return buffer;

    }

    private SocketChannel getSocketChannel() {
        return (SocketChannel)clientKey.channel();
    }

}