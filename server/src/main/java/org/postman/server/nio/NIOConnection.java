package org.postman.server.nio;

import android.support.annotation.VisibleForTesting;

import com.siia.commons.core.check.Check;
import com.siia.commons.core.inject.Provider;
import com.siia.commons.core.log.Logcat;

import org.postman.server.Connection;
import org.postman.server.PostmanMessage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import static com.siia.commons.core.io.IO.closeQuietly;
import static java.util.Objects.nonNull;

/**
 * An implementation of {@link Connection} for non blocking NIO based server implementation.
 * This class does not do any of the actually connection setup but rather represents an already connected
 * connection. See {@link NIOConnectionFactory} for the connection setup.
 * This class uses a {@link ConcurrentLinkedQueue} to hold {@link PostmanMessage}'s that have been read
 * and still need to be sent. Manages the necessary {@link SelectionKey} operations when needing
 * to send messages.
 *
 */
class NIOConnection implements Connection {
    private static final String TAG = Logcat.getTag();
    private static final int BUFFER_SIZE = 4096;
    private final ByteBuffer buffer;
    private final SocketChannel clientSocketChannel;
    private final UUID connectionId;
    private Provider<PostmanMessage> messageProvider;
    private SelectionKey selectionKey;
    private final Queue<PostmanMessage> readMessages;
    private final Queue<PostmanMessage> messagesToSend;

    NIOConnection(SocketChannel clientSocketChannel, Provider<PostmanMessage> messageProvider, SelectionKey clientKey) {
        this(UUID.randomUUID(), clientSocketChannel, messageProvider, ByteBuffer.allocate(BUFFER_SIZE), clientKey);
    }

    @VisibleForTesting
    NIOConnection(UUID connectionId, SocketChannel clientSocketChannel, Provider<PostmanMessage> messageProvider,
                  ByteBuffer buffer, SelectionKey selectionKey) {
        this.clientSocketChannel = clientSocketChannel;
        this.connectionId = connectionId;
        this.messageProvider = messageProvider;
        this.buffer = buffer;
        this.readMessages = new ConcurrentLinkedQueue<>();
        this.messagesToSend = new ConcurrentLinkedQueue<>();
        this.selectionKey = selectionKey;
    }

    void read() throws IOException {
        PostmanMessage currentMessage = readMessages.peek();

        if (currentMessage == null || currentMessage.isInitialised()) {
            currentMessage = messageProvider.get();
            readMessages.offer(currentMessage);
        }

        buffer.clear();

        int bytesRead;

        while ((bytesRead = clientSocketChannel.read(buffer)) > 0) {

            Logcat.v(TAG, connectionId, "read %d bytes", bytesRead);
            buffer.flip();

            while (buffer.hasRemaining()) {
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
        if (selectionKey.isValid()) {
            selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
        }
    }

    void unsetWriteInterest() {
        if (selectionKey.isValid()) {
            selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
        }

    }

    @Override
    public UUID getConnectionId() {
        return connectionId;
    }

    @Override
    public void disconnect() {
        closeQuietly(clientSocketChannel);
        selectionKey.cancel();
        readMessages.clear();
        messagesToSend.clear();
    }


    @Override
    public boolean isConnected() {
        return clientSocketChannel.isConnected() && selectionKey.isValid();
    }

    SelectionKey selectionKey() {
        return selectionKey;
    }

    private boolean sendMessage(PostmanMessage msg) throws IOException {
        ByteBuffer out = msg.getFrame();

        int numWritten = 0;

        while (selectionKey.isWritable() && out.hasRemaining() && selectionKey.isValid() && isConnected()) {
            int outBytes = clientSocketChannel.write(out);
            numWritten += outBytes;
            Logcat.v(TAG, getConnectionId(), "wrote %d / %d bytes", numWritten, out.limit());
        }

        return !out.hasRemaining();
    }

    void sendAnyPendingMessages() throws IOException {
        if (messagesToSend.isEmpty()) {
            unsetWriteInterest();
            return;
        }

        while (!messagesToSend.isEmpty()) {
            PostmanMessage msg = messagesToSend.peek();
            if (nonNull(msg)) {

                Logcat.v(TAG, getConnectionId(), "Sending msg : " + msg.toString());
                if (sendMessage(msg)) {
                    messagesToSend.remove(msg);
                } else {
                    //Break out so that we can continue sending the same message in the next loop.
                    //TODO We dont support out of order messages yet
                    break;
                }

            }
        }

        if (messagesToSend.isEmpty() && isConnected()) {
            unsetWriteInterest();
        }

    }

    List<PostmanMessage> filledMessages() {
        List<PostmanMessage> readyMessages = readMessages.stream().filter(PostmanMessage::isInitialised).collect(Collectors.toList());
        readMessages.removeAll(readyMessages);
        return readyMessages;
    }

    @Override
    public void queueMessageToSend(PostmanMessage msg) {
        Check.checkState(isConnected(), "Connection is not valid");

        if (!messagesToSend.offer(msg)) {
            Logcat.e(TAG, "Could not add message [%s] to queue, dropping", msg.toString());
            return;
        }

        setWriteInterest();
        selectionKey.selector().wakeup();

    }

    @Override
    public String toString() {

        String toString = "NIOConnection{" +
                " co=" + clientSocketChannel.isOpen() +
                " cc=" + clientSocketChannel.isConnected() +
                " skv=" + selectionKey.isValid();

        if (selectionKey != null && selectionKey.isValid()) {
            toString += " skw=" + selectionKey.isWritable() +
                    " skr=" + selectionKey.isReadable() +
                    " skc=" + selectionKey.isConnectable() +
                    " ska=" + selectionKey.isAcceptable();
        }


        return toString + " connectionId=" + connectionId +
                " }";
    }

}