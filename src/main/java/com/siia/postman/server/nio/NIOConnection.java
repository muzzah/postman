package com.siia.postman.server.nio;

import android.support.annotation.NonNull;

import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.Connection;
import com.siia.postman.server.PostmanMessage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import javax.inject.Provider;

import static com.siia.commons.core.io.IO.closeQuietly;

class NIOConnection implements Connection {
    private static final String TAG = Logcat.getTag();
    private static final int BUFFER_SIZE = 4096;
    private final ByteBuffer buffer;

    private final SocketChannel clientSocketChannel;
    private final UUID connectionId;
    private Provider<PostmanMessage> messageProvider;
    private SelectionKey selectionKey;
    private Queue<PostmanMessage> readMessages;

    NIOConnection(SocketChannel clientSocketChannel, Provider<PostmanMessage> messageProvider, SelectionKey clientKey) {
        this(UUID.randomUUID(), clientSocketChannel,messageProvider,  ByteBuffer.allocate(BUFFER_SIZE),
                new ConcurrentLinkedQueue<>());
        this.selectionKey = clientKey;
    }

    NIOConnection(SocketChannel clientSocketChannel, Provider<PostmanMessage> messageProvider) {
        this(UUID.randomUUID(), clientSocketChannel,messageProvider,  ByteBuffer.allocate(BUFFER_SIZE),
                new ConcurrentLinkedQueue<>());
    }


    NIOConnection(UUID connectionId, SocketChannel clientSocketChannel, Provider<PostmanMessage> messageProvider,
                  ByteBuffer buffer, Queue<PostmanMessage> readMessages) {
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

            Logcat.v(TAG, connectionId, "read %d bytes", bytesRead);
            buffer.flip();

            while(buffer.hasRemaining()) {
                if (currentMessage.read(buffer)) {
                    //TODO We may have read in less than the frame if a frame over lap occurs here
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

    void setWriteInterest() {
        selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
    }

    void unsetWriteInterest() {
        selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE);

    }

    void destroy() {
        selectionKey.cancel();
        closeQuietly(clientSocketChannel);
    }

    @Override
    public UUID getConnectionId() {
        return connectionId;
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
        ByteBuffer out = msg.frame();
        int numWritten = 0;

        while (selectionKey.isWritable() && out.hasRemaining() && selectionKey.isValid() && clientSocketChannel.isOpen()) {
            int outBytes = clientSocketChannel.write(out);
            numWritten += outBytes;
            Logcat.v(TAG,getConnectionId(),"wrote %d / %d bytes",  numWritten, out.limit());
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