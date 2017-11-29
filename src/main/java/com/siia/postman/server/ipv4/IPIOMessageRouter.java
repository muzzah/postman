package com.siia.postman.server.ipv4;

import android.annotation.SuppressLint;
import android.util.Log;

import com.siia.commons.core.io.IO;
import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.PostmanMessage;
import com.siia.postman.server.PostmanServerClient;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

import io.reactivex.Completable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static com.siia.commons.core.check.Check.checkState;
import static com.siia.commons.core.log.Logcat.v;


class IPIOMessageRouter {
    private static final String TAG = Logcat.getTag();

    private Selector readWriteSelector;
    private final ConcurrentMap<SelectionKey, IPPostmanServerClient> connectedClientsBySelectionKey;
    private final ConcurrentMap<PostmanServerClient, BlockingQueue<PostmanMessage>> messageQueueForEachClient;
    private final List<IPPostmanServerClient> clientsToRegister;
    private PublishSubject<IPMessageRouterEvent> messageRouterEventStream;

    @SuppressLint("UseSparseArrays")
    IPIOMessageRouter() {
        this.messageRouterEventStream = PublishSubject.create();
        this.connectedClientsBySelectionKey = new ConcurrentHashMap<>();
        this.messageQueueForEachClient = new ConcurrentHashMap<>();
        this.clientsToRegister = Collections.synchronizedList(new ArrayList<>());

    }

    void shutdown() {
        connectedClientsBySelectionKey.values().forEach(IPPostmanServerClient::destroy);
        connectedClientsBySelectionKey.clear();

        clientsToRegister.clear();

        IO.closeQuietly(readWriteSelector);

        messageQueueForEachClient.clear();

    }

    boolean isRunning() {
        return readWriteSelector != null && readWriteSelector.isOpen();
    }

    void addMessageToQueue(PostmanMessage msg, PostmanServerClient destination) {

        IPPostmanServerClient serverClient = (IPPostmanServerClient) destination;

        if (!serverClient.isValid()) {
            Logcat.w(TAG, "Not adding message [%s] to queue with invalid client [%s]", msg.toString(), destination.toString());
            return;
        }

        BlockingQueue<PostmanMessage> queueForClient = messageQueueForEachClient.get(serverClient);

        if (!queueForClient.offer(msg)) {
            Logcat.e(TAG, "Could not add message [%s] to queue, dropping", msg.toString());
            return;
        }

        try {
            serverClient.setWriteInterest();
        } catch (ClosedChannelException e) {
            Logcat.e(TAG, "Could not set write interest for client selector", e);
            cleanupClient(serverClient);
        }
        readWriteSelector.wakeup();


    }

    private void cleanupClient(IPPostmanServerClient client) {
        Logcat.v(TAG, "Destroying client %s", client.getClientId());
        client.destroy();
        SelectionKey clientKey = client.selectionKey();
        if(client.selectionKey() != null ) {

            if (connectedClientsBySelectionKey.containsKey(clientKey)) {
                connectedClientsBySelectionKey.remove(clientKey);

            }
        }


        if (messageQueueForEachClient.containsKey(client)) {
            messageQueueForEachClient.remove(client);
        }

        messageRouterEventStream.onNext(IPMessageRouterEvent.clientUnregistered(client));



    }

    void startMessageQueueLoop() {
        checkState(!isRunning(), "Message Queue alrady running");

        Completable.create(completableEmitter -> {

            try{
                readWriteSelector = Selector.open();
            } catch (IOException e) {
                Log.e(TAG, "Failed to open selector for writing", e);
                completableEmitter.onError(e);
                return;
            }

            while (true) {

                int channelsReady = readWriteSelector.select();

                if (!readWriteSelector.isOpen()) {
                    break;
                }

                Logcat.v(TAG, "%d clients to register", clientsToRegister.size());
                clientsToRegister.forEach(client -> {
                    SelectionKey clientKey;
                    try {
                        clientKey = client.channel().register(readWriteSelector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);
                    } catch (ClosedChannelException e) {
                        Log.e(TAG, "Connected client disconnected before write ops registration", e);
                        client.destroy();
                        return;
                    }

                    client.setSelectionKey(clientKey);
                    connectedClientsBySelectionKey.put(clientKey, client);
                    messageQueueForEachClient.put(client, new LinkedBlockingQueue<>());
                    messageRouterEventStream.onNext(IPMessageRouterEvent.clientRegistered(client));


                });

                clientsToRegister.clear();

                v(TAG, "%s channel(s) ready in write loop", channelsReady);
                v(TAG, "%s keys selected", readWriteSelector.selectedKeys().size());


                readWriteSelector.selectedKeys().forEach(selectionKey -> {
                    v(TAG, "SK : v=%s w=%s c=%s r=%s", selectionKey.isValid(),
                            selectionKey.isWritable(), selectionKey.isConnectable(), selectionKey.isReadable());

                    IPPostmanServerClient client = connectedClientsBySelectionKey.get(selectionKey);

                    if (!selectionKey.isValid()) {
                        cleanupClient(client);
                        return;
                    }

                    BlockingQueue<PostmanMessage> messagesForClient = messageQueueForEachClient.get(client);


                    if(selectionKey.isReadable()) {

                        try {
                            ByteBuffer in = client.read();
                        } catch (IOException e) {
                            Logcat.w(TAG, "Lost client : %s", e.getMessage());
                            cleanupClient(client);
                            return;
                        }
                    }

                    if (selectionKey.isWritable()) {

                        if (messagesForClient.isEmpty()) {
                            client.unsetWriteInterest();
                            return;
                        }

                        PostmanMessage msg = messagesForClient.poll();
                        if (msg != null) {

                            try {
                                if(client.sendMessage(msg)) {
                                    client.unsetWriteInterest();
                                }
                            } catch (NonWritableChannelException e) {
                                Log.e(TAG, "Channel not writable", e);
                            } catch (IOException e) {
                                cleanupClient(client);
                                Log.e(TAG, "Problem sending message", e);

                            }

                        } else {
                            Logcat.w(TAG, "Selector write ops received but no message in queue");
                        }

                    }
                });
                readWriteSelector.selectedKeys().clear();

            }

            Logcat.d(TAG, "Message Queue Loop Exited");
            completableEmitter.onComplete();

        }).subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .subscribe(
                        () -> Logcat.i(TAG, "Message queue loop completed"),
                        error -> Logcat.e(TAG, "Error in Message queue loop", error)
                        //TODO These events needs to propogate up. Review error handling when doing this
                );

    }

    void addClient(IPPostmanServerClient client) {
        clientsToRegister.add(client);
        readWriteSelector.wakeup();
    }

    PublishSubject<IPMessageRouterEvent> messageRouterEventStream() {
        return messageRouterEventStream;
    }

}
