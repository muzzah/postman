package com.siia.postman.server.nio;

import com.siia.commons.core.io.IO;
import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.PostmanMessage;
import com.siia.postman.server.Connection;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.UUID;

class NIOConnection implements Connection {
    private static final String TAG = Logcat.getTag();
    private static final int BUFFER_SIZE = 4096;
    private final ByteBuffer buffer;

    private final SocketChannel clientSocketChannel;
    private final UUID clientId;
    private SelectionKey selectionKey;
    private PostmanMessage currentMessage;

    NIOConnection(UUID clientId, SocketChannel clientSocketChannel) {
        this.clientSocketChannel = clientSocketChannel;
        this.clientId = clientId;
        buffer = ByteBuffer.allocate(BUFFER_SIZE);
    }

    boolean read() throws IOException {
        if(currentMessage == null || currentMessage.isFilled()) {
            currentMessage = new PostmanMessage();
        }

        buffer.clear();

        int bytesRead;

        while((bytesRead = getSocketChannel().read(buffer)) > 0) {

            Logcat.v(TAG, "[%s] read %d bytes", clientId.toString(), bytesRead);
            buffer.flip();
            if(currentMessage.read(buffer)) {
                //TODO We may have read in less than the buffer if a frame over lap occurs here
                Logcat.v(TAG, "Message read : [%s]", currentMessage.toString());
                return true;
            }

            buffer.clear();
        }

        if (bytesRead == -1) {
            throw new IOException("Invalid bytes read from channel");
        }

        return false;
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

    @Override
    public boolean isValid() {
        //Adding selector key validity check here causes potential race condition with adding message to queue
        // as the key is registered in the message queue loop
        return clientSocketChannel.isConnected();
    }

    SelectionKey selectionKey() {
        return selectionKey;
    }

    boolean sendMessage(PostmanMessage msg) throws IOException {
        ByteBuffer out = msg.getFrame();
        Logcat.v(TAG, Connection.logMsg("Sending msg : " + msg.toString(), getClientId()));
        int numWritten = 0;

        while (selectionKey.isWritable() && out.hasRemaining() && selectionKey.isValid() && clientSocketChannel.isOpen()) {
            numWritten += clientSocketChannel.write(out);
            Logcat.v(TAG, Connection.logMsg("wrote %d / %d bytes", getClientId(), numWritten, out.limit()));
        }

        if (numWritten == 0) {
            Logcat.w(TAG, Connection.logMsg("0 bytes of message written [v=%s o=%s]",
                    getClientId(), selectionKey.isValid(), clientSocketChannel.isOpen()));
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

        String toString = "NIOConnection{" +
                " co=" + clientSocketChannel.isOpen() +
                " cc=" + clientSocketChannel.isConnected();
        if (selectionKey != null && selectionKey.isValid()) {
            toString += " skv=" + selectionKey.isValid() +
                    " skw=" + selectionKey.isWritable() +
                    " skr=" + selectionKey.isReadable() +
                    " skc=" + selectionKey.isConnectable() +
                    " ska=" + selectionKey.isAcceptable();
        }


        return toString + " clientId=" + clientId +
                " }";
    }


    PostmanMessage getNextMessage() {
        return currentMessage;
    }
}