package com.siia.postman.server.nio;

import android.annotation.SuppressLint;
import android.util.Log;

import com.siia.commons.core.io.IO;
import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.Connection;
import com.siia.postman.server.PostmanMessage;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

import javax.inject.Named;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.subjects.PublishSubject;

import static com.siia.commons.core.log.Logcat.v;


class MessageQueueLoop {
    private static final String TAG = Logcat.getTag();

    private Selector readWriteSelector;
    private final ConcurrentMap<SelectionKey, NIOConnection> connectedClientsBySelectionKey;
    private final ConcurrentMap<Connection, BlockingQueue<PostmanMessage>> messageQueueForEachClient;
    private final List<NIOConnection> clientsToRegister;
    private PublishSubject<MessageQueueEvent> messageRouterEventStream;
    private Scheduler newThreadScheduler;

    @SuppressLint("UseSparseArrays")
    MessageQueueLoop(@Named("new") Scheduler newThreadScheduler) {
        this.newThreadScheduler = newThreadScheduler;
        this.messageRouterEventStream = PublishSubject.create();
        this.connectedClientsBySelectionKey = new ConcurrentHashMap<>();
        this.messageQueueForEachClient = new ConcurrentHashMap<>();
        this.clientsToRegister = Collections.synchronizedList(new ArrayList<>());

    }

    void shutdown() {

        connectedClientsBySelectionKey.values().forEach(NIOConnection::destroy);
        connectedClientsBySelectionKey.clear();

        clientsToRegister.clear();


        messageQueueForEachClient.clear();

        if (isRunning()) {
            IO.closeQuietly(readWriteSelector);
        }


    }

    boolean isRunning() {
        return readWriteSelector != null && readWriteSelector.isOpen();
    }

    boolean addMessageToQueue(PostmanMessage msg, Connection destination) {

        NIOConnection NIOConnection = (NIOConnection) destination;

        if (!NIOConnection.isValid()) {
            Logcat.w(TAG, "Not adding message [%s] to queue with invalid connection [%s]", msg.toString(), destination.toString());
            return false;
        }

        BlockingQueue<PostmanMessage> queueForClient = messageQueueForEachClient.get(NIOConnection);

        if (!queueForClient.offer(msg)) {
            Logcat.e(TAG, "Could not add message [%s] to queue, dropping", msg.toString());
            return false;
        }

        try {
            NIOConnection.setWriteInterest();
        } catch (ClosedChannelException e) {
            Logcat.e(TAG, "Could not set write interest for connection selector", e);
            cleanupConnection(NIOConnection);
            return false;
        }
        readWriteSelector.wakeup();
        return true;


    }

    private void cleanupConnection(NIOConnection client) {
        Logcat.v(TAG, "Destroying connection %s", client.getConnectionId());
        client.destroy();
        SelectionKey clientKey = client.selectionKey();
        if (client.selectionKey() != null) {

            if (connectedClientsBySelectionKey.containsKey(clientKey)) {
                connectedClientsBySelectionKey.remove(clientKey);

            }
        }


        if (messageQueueForEachClient.containsKey(client)) {
            messageQueueForEachClient.remove(client);
        }

        messageRouterEventStream.onNext(MessageQueueEvent.clientUnregistered(client));


    }

    /**
     * OnError - Called when loop exits due unexpected error
     * onComplete - Called after a graceful shutdown
     */
    Flowable<MessageQueueEvent> startMessageQueueLoop() {
        if (isRunning()) {
            Logcat.w(TAG, "Message Queue already running");
            return Flowable.<MessageQueueEvent>create(
                    emitter -> emitter.onError(new IllegalStateException("Already running the loop")),
                    BackpressureStrategy.LATEST)
                    .subscribeOn(newThreadScheduler);
        }

        Logcat.d(TAG, "Initialising message loop");

        return Flowable.<MessageQueueEvent>create(
                emitter -> {
                    Logcat.v(TAG, "At start Message Queue");
                    try {
                        readWriteSelector = Selector.open();
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to open selector for read/write", e);
                        emitter.onError(e);
                        return;
                    }

                    Logcat.v(TAG, "Firing ready event");
                    emitter.onNext(MessageQueueEvent.ready());

                    try {
                        while (true) {
                            int channelsReady = readWriteSelector.select();

                            if (!readWriteSelector.isOpen()) {
                                break;
                            }

                            registerClientsIfNeeded();


                            v(TAG, "%s channel(s) ready in write loop", channelsReady);

                            processKeysWithUpdates();

                        }
                    } catch (Throwable e) {

                    }

                    Logcat.d(TAG, "Message Queue Loop Exited");
                    emitter.onComplete();

                }, BackpressureStrategy.BUFFER)
                .subscribeOn(newThreadScheduler);

    }

    private void processKeysWithUpdates() {
        Set<SelectionKey> selectedKeys = readWriteSelector.selectedKeys();

        v(TAG, "%s keys selected", selectedKeys.size());

        selectedKeys.forEach(selectionKey -> {
            v(TAG, "SK : valid=%s writable=%s connectable=%s readbale=%s", selectionKey.isValid(),
                    selectionKey.isWritable(), selectionKey.isConnectable(), selectionKey.isReadable());

            NIOConnection connection = connectedClientsBySelectionKey.get(selectionKey);

            if (!selectionKey.isValid()) {
                cleanupConnection(connection);
                return;
            }

            //TODO these read/writes should really happen in separate threads
            if (selectionKey.isReadable()) {

                try {
                    connection.read();
                } catch (Exception e) {
                    Logcat.w(TAG, "Lost connection : %s", e.getMessage());
                    cleanupConnection(connection);
                    return;
                }

                connection.filledMessages().forEach(msg -> {
                    Logcat.v(TAG, "Message received [%s]", msg.toString());

                    messageRouterEventStream.onNext(MessageQueueEvent.messageReceived(connection, msg));
                });
            }

            if (selectionKey.isWritable()) {
                BlockingQueue<PostmanMessage> messagesForClient = messageQueueForEachClient.get(connection);

                if (messagesForClient.isEmpty()) {
                    connection.unsetWriteInterest();
                    return;
                }

                PostmanMessage msg = messagesForClient.peek();
                if (msg != null) {

                    try {
                        Logcat.v(TAG, connection.getConnectionId(), "Sending msg : " + msg.toString());
                        if (connection.sendMessage(msg)) {
                            connection.unsetWriteInterest();
                            messagesForClient.remove(msg);
                        }
                    } catch (NonWritableChannelException e) {
                        Log.e(TAG, "Channel not writable", e);
                    } catch (IOException e) {
                        Log.e(TAG, "Problem sending message", e);
                        cleanupConnection(connection);

                    }

                } else {
                    Logcat.w(TAG, "Selector write ops received but no message in queue");
                    connection.unsetWriteInterest();
                }

            }
        });
        selectedKeys.clear();
    }

    private void registerClientsIfNeeded() {
        Logcat.v(TAG, "%d clients to register", clientsToRegister.size());
        clientsToRegister.forEach(client -> {
            SelectionKey clientKey;
            try {
                //Start with a write interest to send any queued up msgs, loop below will unset connection if needed
                clientKey = client.channel().register(readWriteSelector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);
            } catch (ClosedChannelException e) {
                Log.e(TAG, "Connected connection disconnected before write ops registration", e);
                client.destroy();
                messageRouterEventStream.onNext(MessageQueueEvent.clientRegistrationFailed(client));
                return;
            }

            client.setSelectionKey(clientKey);
            connectedClientsBySelectionKey.put(clientKey, client);
            messageQueueForEachClient.put(client, new LinkedBlockingQueue<>());
            messageRouterEventStream.onNext(MessageQueueEvent.clientRegistered(client));
        });

        clientsToRegister.clear();

    }

    void addClient(NIOConnection client) {
        clientsToRegister.add(client);
        readWriteSelector.wakeup();
    }

    PublishSubject<MessageQueueEvent> messageQueueEventsStream() {
        return messageRouterEventStream;
    }
}
