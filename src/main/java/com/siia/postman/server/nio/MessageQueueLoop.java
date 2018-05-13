package com.siia.postman.server.nio;

import android.util.Log;

import com.siia.commons.core.io.IO;
import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.Connection;
import com.siia.postman.server.PostmanMessage;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;
import javax.inject.Named;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.subjects.PublishSubject;

import static com.siia.commons.core.log.Logcat.v;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Single threaded message queue which handles the reading and writing of
 * PostmanMessages to connected clients. Clients need to be registered before
 * one can send or receive messages. A registration event will be fired once client is registered
 * and ready to be communicated with
 */
class MessageQueueLoop {
    private static final String TAG = Logcat.getTag();

    private Selector readWriteSelector;
    private final ConcurrentMap<SelectionKey, NIOConnection> connectedClientsBySelectionKey;
    private final ConcurrentMap<Connection, BlockingQueue<PostmanMessage>> messageQueueForEachClient;
    private final List<NIOConnection> clientsToRegister;
    private PublishSubject<MessageQueueEvent> messageRouterEventStream;
    private Scheduler newThreadScheduler;
    private final SelectorProvider selectorProvider;
    private final AtomicBoolean shouldLoop;

    @Inject
    MessageQueueLoop(@Named("new") Scheduler newThreadScheduler, SelectorProvider selectorProvider) {
        this.newThreadScheduler = newThreadScheduler;
        this.selectorProvider = selectorProvider;
        this.messageRouterEventStream = PublishSubject.create();
        this.connectedClientsBySelectionKey = new ConcurrentHashMap<>();
        this.messageQueueForEachClient = new ConcurrentHashMap<>();
        this.clientsToRegister = Collections.synchronizedList(new ArrayList<>());
        this.shouldLoop = new AtomicBoolean(false);
    }

    void shutdown() {
        shouldLoop.set(false);
        connectedClientsBySelectionKey.values().forEach(NIOConnection::disconnect);
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

    protected boolean addMessageToQueue(PostmanMessage msg, Connection destination) {

        NIOConnection NIOConnection = (NIOConnection) destination;

        if (!NIOConnection.isConnected()) {
            Logcat.w(TAG, "Not adding message [%s] to queue with invalid connection [%s]", msg.toString(), destination.toString());
            return false;
        }

        BlockingQueue<PostmanMessage> queueForClient = messageQueueForEachClient.getOrDefault(NIOConnection, new LinkedBlockingQueue<>());

        if (!queueForClient.offer(msg)) {
            Logcat.e(TAG, "Could not add message [%s] to queue, dropping", msg.toString());
            return false;
        }

        messageQueueForEachClient.put(NIOConnection, queueForClient);

        try {
            NIOConnection.setWriteInterest();
        } catch (Throwable e) {
            Logcat.e(TAG, "Could not set write interest for connection selector", e);
            cleanupConnection(NIOConnection);
            return false;
        }
        readWriteSelector.wakeup();
        return true;


    }

    private void cleanupConnection(NIOConnection client) {
        Logcat.v(TAG, "Destroying connection %s", client.getConnectionId());
        client.disconnect();
        SelectionKey clientKey = client.selectionKey();
        if (client.selectionKey() != null) {

            if (connectedClientsBySelectionKey.containsKey(clientKey)) {
                connectedClientsBySelectionKey.remove(clientKey);

            }
        }


        if (messageQueueForEachClient.containsKey(client)) {
            messageQueueForEachClient.get(client).clear();
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
            return Flowable.<MessageQueueEvent>error(new IllegalStateException("Already running the loop"))
                    .subscribeOn(newThreadScheduler);
        }

        Logcat.d(TAG, "Initialising message loop");

        try {
            readWriteSelector = selectorProvider.openSelector();
        } catch (IOException e) {
            Log.e(TAG, "Failed to open selector for read/write", e);
            return Flowable.<MessageQueueEvent>error(e)
                    .subscribeOn(newThreadScheduler);
        }

        return Flowable.<MessageQueueEvent>create(
                emitter -> {
                    Logcat.v(TAG, "Enter Msg Queue Loop");
                    emitter.onNext(MessageQueueEvent.ready());

                    try {
                        shouldLoop.set(true);

                        while (shouldLoop.get()) {
                            int channelsReady = readWriteSelector.select();

                            if (!readWriteSelector.isOpen() || !shouldLoop.get()) {
                                break;
                            }

                            registerClientsIfNeeded();


                            v(TAG, "%s channel(s) ready in write loop", channelsReady);

                            processKeysWithUpdates();

                        }
                        Logcat.d(TAG, "Message Queue Loop Exited");
                        shutdown();

                        if(!emitter.isCancelled()) {
                            emitter.onComplete();
                        }

                    } catch (Throwable e) {
                        Logcat.e(TAG, "Error in Message Queue Loop", e);
                        if(!emitter.isCancelled()) {
                            emitter.onError(e);
                        }
                        shutdown();
                    }
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
                    Logcat.e(TAG, "Lost connection", e);
                    cleanupConnection(connection);
                }

                if(connection.isConnected()) {
                    connection.filledMessages().forEach(msg -> {
                        Logcat.v(TAG, "Message received [%s]", msg.toString());

                        messageRouterEventStream.onNext(MessageQueueEvent.messageReceived(connection, msg));
                    });
                }
            }

            if (selectionKey.isWritable()) {
                BlockingQueue<PostmanMessage> messagesForClient = messageQueueForEachClient.get(connection);

                if (isNull(messagesForClient) || messagesForClient.isEmpty()) {
                    connection.unsetWriteInterest();
                    return;
                }

                while(!messagesForClient.isEmpty()) {
                    PostmanMessage msg = messagesForClient.peek();
                    if (nonNull(msg)) {
                        try {
                            Logcat.v(TAG, connection.getConnectionId(), "Sending msg : " + msg.toString());
                            if (connection.sendMessage(msg)) {
                                messagesForClient.remove(msg);
                            }
                        } catch (Throwable e) {
                            Log.e(TAG, "Problem sending message", e);
                            cleanupConnection(connection);
                        }

                    }
                }

                if(messagesForClient.isEmpty() && connection.isConnected()) {
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
            } catch (Throwable e) {
                Log.e(TAG, "Connected connection disconnected before write ops registration", e);
                client.disconnect();
                messageRouterEventStream.onNext(MessageQueueEvent.clientRegistrationFailed(client));
                return;
            }

            client.setSelectionKey(clientKey);
            connectedClientsBySelectionKey.put(clientKey, client);
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

    void stopLoop() {
        shouldLoop.set(false);
    }
}
