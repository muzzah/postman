package com.siia.postman.server.nio;

import android.support.annotation.NonNull;

import com.siia.commons.core.io.IO;
import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.PostmanMessage;
import com.siia.postman.server.Connection;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import javax.inject.Provider;

class NIOConnection implements Connection {
    private static final String TAG = Logcat.getTag();
    private static final int BUFFER_SIZE = 4096;
    private final ByteBuffer buffer;

    private final SocketChannel clientSocketChannel;
    private final UUID connectionId;
    private Provider<PostmanMessage> messageProvider;
    private SelectionKey selectionKey;
    private Queue<PostmanMessage> readMessages;

    NIOConnection(UUID connectionId, SocketChannel clientSocketChannel, Provider<PostmanMessage> messageProvider) {
        this(connectionId,clientSocketChannel,messageProvider,  ByteBuffer.allocate(BUFFER_SIZE), new ConcurrentLinkedQueue<>());
    }


    NIOConnection(UUID connectionId, SocketChannel clientSocketChannel, Provider<PostmanMessage> messageProvider, ByteBuffer buffer, Queue<PostmanMessage> readMessages) {
        this.clientSocketChannel = clientSocketChannel;
        this.connectionId = connectionId;
        this.messageProvider = messageProvider;
        this.buffer = buffer;
        this.readMessages = readMessages;
    }

    void read() throws IOException {
        PostmanMessage currentMessage = readMessages.peek();

        if(currentMessage == null || currentMessage.isFull()) {
            currentMessage = messageProvider.get();
            readMessages.offer(currentMessage);
        }

        buffer.clear();

        int bytesRead;

        while((bytesRead = clientSocketChannel.read(buffer)) > 0) {

            Logcat.v(TAG, "[%s] read %d bytes", connectionId.toString(), bytesRead);
            buffer.flip();

            while(buffer.hasRemaining()) {
                if (currentMessage.read(buffer)) {
                    //TODO We may have read in less than the buffer if a frame over lap occurs here
                    Logcat.v(TAG, "Message read : [%s]", currentMessage.toString());
                    currentMessage = messageProvider.get();
                    readMessages.offer(currentMessage);
                }
            }

            buffer.clear();
        }

        if (bytesRead == -1) {
            throw new IOException("Invalid bytes read from channel");
        }
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
    public UUID getConnectionId() {
        return connectionId;
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
        ByteBuffer out = msg.buffer();
        Logcat.v(TAG, Connection.logMsg("Sending msg : " + msg.toString(), getConnectionId()));
        int numWritten = 0;

        while (selectionKey.isWritable() && out.hasRemaining() && selectionKey.isValid() && clientSocketChannel.isOpen()) {
            int outBytes = clientSocketChannel.write(out);
            numWritten += outBytes;
            Logcat.v(TAG, Connection.logMsg("wrote %d / %d bytes", getConnectionId(), numWritten, out.limit()));
        }

        if (numWritten == 0) {
            Logcat.w(TAG, Connection.logMsg("0 bytes of message written [v=%s o=%s]",
                    getConnectionId(), selectionKey.isValid(), clientSocketChannel.isOpen()));
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


        return toString + " connectionId=" + connectionId +
                " }";
    }


    PostmanMessage getNextMessage() {
        return null;
    }

    List<PostmanMessage> filledMessages() {
        List<PostmanMessage> readyMessages = readMessages.stream().filter(PostmanMessage::isFull).collect(Collectors.toList());
        readMessages.removeAll(readyMessages);
        return readyMessages;
    }

    @Override
    public int compareTo(@NonNull Connection o) {
        return o.getConnectionId().compareTo(connectionId);
    }
}