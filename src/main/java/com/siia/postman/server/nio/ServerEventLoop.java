package com.siia.postman.server.nio;


import android.util.Log;

import com.siia.commons.core.io.IO;
import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.Connection;
import com.siia.postman.server.PostmanMessage;
import com.siia.postman.server.PostmanServerEvent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;
import javax.inject.Named;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.Scheduler;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.CompositeDisposable;

import static com.siia.commons.core.log.Logcat.d;
import static com.siia.commons.core.log.Logcat.v;
import static java.util.Objects.nonNull;

class ServerEventLoop {
    private static final String TAG = Logcat.getTag();

    private ServerSocketChannel serverSocketChannel;
    private Selector nioSelector;
    private InetSocketAddress bindAddress;
    private SelectorProvider selectorProvider;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private SelectionKey acceptSelectionKey;
    private Scheduler newThreadScheduler;
    private final AtomicBoolean shouldLoop;
    private final CompositeDisposable disposables;
    private final ConcurrentMap<SelectionKey, NIOConnection> connectedClientsBySelectionKey;
    private final NIOConnectionFactory nioConnectionFactory;


    @Inject
    ServerEventLoop(SelectorProvider selectorProvider,
                    NIOConnectionFactory nioConnectionFactory,
                    @Named("new") Scheduler newThreadScheduler) {
        this.selectorProvider = selectorProvider;
        this.nioConnectionFactory = nioConnectionFactory;
        this.newThreadScheduler = newThreadScheduler;
        this.connectedClientsBySelectionKey = new ConcurrentHashMap<>();
        this.disposables = new CompositeDisposable();
        shouldLoop = new AtomicBoolean(false);
    }

    void shutdownLoop() {
        shouldLoop.set(false);
        disposables.clear();
        connectedClientsBySelectionKey.values().forEach(NIOConnection::disconnect);
        IO.closeQuietly(serverSocketChannel);
        IO.closeQuietly(nioSelector);
        connectedClientsBySelectionKey.clear();
    }

    boolean isRunning() {
        return nonNull(serverSocketChannel) && serverSocketChannel.isOpen() && !serverSocketChannel.socket().isClosed();
    }

    //TODO handle the return value in callers
    boolean addMessageToQueue(PostmanMessage msg, Connection destination) {

        NIOConnection NIOConnection = (NIOConnection) destination;

        if (!NIOConnection.isConnected()) {
            //No need to destroy and the main loop should handle it
            Logcat.w(TAG, "Not adding message [%s] to queue with invalid connection [%s]", msg.toString(), destination.toString());
            return false;
        }

        try {
            NIOConnection.addMessageToSend(msg);
        } catch (Throwable e) {
            Logcat.e(TAG, "Could not set write interest for connection selector", e);
            cleanupConnection(NIOConnection);
            return false;
        }


        nioSelector.wakeup();

        return true;

    }


    Flowable<PostmanServerEvent> startLooping(@NonNull InetSocketAddress bindAddress) {
        Logcat.d(TAG, "Initialising Server Event Loop");
        this.bindAddress = bindAddress;

        return Flowable.<PostmanServerEvent>create(emitter -> {
            Logcat.d(TAG, "Beginning to listen to clients");

            try {
                initialiseServerSocket();
            } catch (Exception e) {
                shutdownLoop();
                emitter.onError(e);
                return;
            }


            emitter.onNext(PostmanServerEvent.serverListening(bindAddress.getPort(), bindAddress.getHostName()));
            try {
                shouldLoop.set(true);
                while (shouldLoop.get()) {
                    v(TAG, "Waiting for selector updates");
                    int channelsReady = nioSelector.select();

                    if (!nioSelector.isOpen() || !isRunning() || !shouldLoop.get()) {
                        break;
                    }

                    if (nioSelector.selectedKeys().isEmpty()) {
                        Logcat.w(TAG, "Selected keys are empty");
                        continue;
                    }

                    v(TAG, "%s channel(s) ready in accept loop", channelsReady);

                    processKeyUpdates(emitter);

                }

                if (!emitter.isCancelled()) {
                    emitter.onComplete();
                }
            } catch (Exception e) {
                emitter.tryOnError(e);
            } finally {
                shutdownLoop();
            }
        }, BackpressureStrategy.BUFFER)
                .subscribeOn(newThreadScheduler);

    }


    private void processKeyUpdates(FlowableEmitter<PostmanServerEvent> emitter) {
        nioSelector.selectedKeys().forEach(selectionKey -> {

            NIOConnection connection = connectedClientsBySelectionKey.get(selectionKey);

            if (!selectionKey.isValid()) {

                if (nonNull(connection)) {
                    cleanupConnection(connection);
                    emitter.onNext(PostmanServerEvent.clientDisconnected(connection));
                }
                return;
            }
            v(TAG, "SK : valid=%b read=%b write=%b accept=%b", selectionKey.isValid(), selectionKey.isReadable(), selectionKey.isWritable(), selectionKey.isAcceptable());

            if (selectionKey.isValid() && selectionKey.isAcceptable()) {
                acceptClientConnection(emitter);
            }

            if (selectionKey.isValid() && selectionKey.isReadable()) {
                handleRead(selectionKey, emitter);
            }

            if (selectionKey.isValid() && selectionKey.isWritable()) {
                handleWrite(selectionKey, emitter);
            }
        });

        nioSelector.selectedKeys().clear();
    }

    private void handleRead(SelectionKey selectionKey, FlowableEmitter<PostmanServerEvent> emitter) {
        NIOConnection connection = connectedClientsBySelectionKey.get(selectionKey);

        try {
            connection.read();
        } catch (Throwable e) {
            Logcat.e(TAG, "Lost connection", e);
            cleanupConnection(connection);
            emitter.onNext(PostmanServerEvent.clientDisconnected(connection));
            return;
        }

        connection.filledMessages().forEach(msg -> {
            Logcat.v(TAG, "Message received [%s]", msg.toString());
            emitter.onNext(PostmanServerEvent.newMessage(msg, connection));
        });
    }

    private void handleWrite(SelectionKey selectionKey, FlowableEmitter<PostmanServerEvent> emitter) {
        NIOConnection connection = connectedClientsBySelectionKey.get(selectionKey);

        try {
            connection.sendMessages();
        } catch (Throwable e) {
            Log.e(TAG, "Problem sending message", e);
            cleanupConnection(connection);
            emitter.onNext(PostmanServerEvent.clientDisconnected(connection));
        }
    }


    private void initialiseServerSocket() throws IOException {

        nioSelector = selectorProvider.openSelector();
        serverSocketChannel = selectorProvider.openServerSocketChannel();
        acceptSelectionKey = nioConnectionFactory.bindServerSocket(nioSelector, serverSocketChannel, bindAddress);

    }

    private void acceptClientConnection(FlowableEmitter<PostmanServerEvent> emitter) {

        d(TAG, "Accepting new connection channel");

        nioConnectionFactory.acceptConnection(serverSocketChannel, nioSelector)
                .ifPresent(nioConnection -> {
                    connectedClientsBySelectionKey.put(nioConnection.selectionKey(), nioConnection);
                    emitter.onNext(PostmanServerEvent.newClient(nioConnection));
                });


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

    }

    public Collection<NIOConnection> getClients() {
        return connectedClientsBySelectionKey.values();
    }
}