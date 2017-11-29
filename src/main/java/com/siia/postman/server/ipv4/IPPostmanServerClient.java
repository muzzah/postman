package com.siia.postman.server.ipv4;

import com.siia.commons.core.io.IO;
import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.PostmanMessage;
import com.siia.postman.server.PostmanServerClient;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.UUID;

import static com.siia.commons.core.log.Logcat.v;

class IPPostmanServerClient implements PostmanServerClient {
    private static final String TAG = Logcat.getTag();
    private static final int BUFFER_SIZE = 4096;

    private final SocketChannel clientSocketChannel;
    private final UUID clientId;
    private SelectionKey selectionKey;

    IPPostmanServerClient(UUID clientId, SocketChannel clientSocketChannel) {
        this.clientSocketChannel = clientSocketChannel;
        this.clientId = clientId;
    }

    ByteBuffer read() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        int bytesRead = getSocketChannel().read(buffer);
        if (bytesRead == -1) {
            throw new IOException("Invalid bytes read from channel");
        }
        v(TAG, "Read %d bytes", bytesRead);
        buffer.flip();
        return buffer;

    }

    void setWriteInterest() throws ClosedChannelException {
        selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
    }

    void unsetWriteInterest() {
        selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE);

    }

    void destroy() {
        if(selectionKey != null) {
            selectionKey.cancel();
        }
        IO.closeQuietly(clientSocketChannel);
    }

    @Override
    public UUID getClientId() {
        return clientId;
    }


    private SocketChannel getSocketChannel() {
        return clientSocketChannel;
    }

    boolean isValid() {
        return clientSocketChannel.isConnected();
    }

    SelectionKey selectionKey() {
        return selectionKey;
    }

    boolean sendMessage(PostmanMessage msg) throws IOException {
        ByteBuffer out = msg.serialise();
        int numWritten = 0;

        while (selectionKey.isWritable() && out.hasRemaining() && selectionKey.isValid() && clientSocketChannel.isOpen()) {
            numWritten += clientSocketChannel.write(out);
            Logcat.v(TAG, "%s wrote %d / %d bytes", clientId.toString(), numWritten, out.limit());
        }

        if (numWritten == 0) {
            Logcat.w(TAG, "0 bytes of message [%s] for client %s [w=%s r=%s sk=%s o=%s]", msg.getMsg(), clientId,
                    selectionKey.isWritable(), out.hasRemaining(), selectionKey.isValid(), clientSocketChannel.isOpen());
        }

        return !out.hasRemaining();
    }

     void setSelectionKey(SelectionKey key) {
        selectionKey = key;
    }

    SocketChannel channel() {
        return clientSocketChannel;
    }

    @Override
    public String toString() {

        String toString = "IPPostmanServerClient{" +
                " co=" + clientSocketChannel.isOpen() +
                " cc=" + clientSocketChannel.isConnected();
        if (selectionKey != null) {
            toString += " skv=" + selectionKey.isValid() +
                    " skw=" + selectionKey.isWritable() +
                    " skr=" + selectionKey.isReadable() +
                    " skc=" + selectionKey.isConnectable() +
                    " ska=" + selectionKey.isAcceptable();
        }


        return toString + " clientId=" + clientId +
                " }";
    }



}