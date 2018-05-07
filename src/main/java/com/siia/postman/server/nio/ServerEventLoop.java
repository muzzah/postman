package com.siia.postman.server.nio;


import android.util.Log;

import com.siia.commons.core.io.IO;
import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.Connection;
import com.siia.postman.server.PostmanMessage;
import com.siia.postman.server.ServerEvent;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.Scheduler;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.CompositeDisposable;

import static com.siia.commons.core.log.Logcat.d;
import static com.siia.commons.core.log.Logcat.v;
import static com.siia.commons.core.log.Logcat.w;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

public class ServerEventLoop {
    private static final String TAG = Logcat.getTag();

    private ServerSocketChannel serverSocketChannel;
    private Selector nioSelector;
    private InetSocketAddress bindAddress;
    private final Provider<PostmanMessage> messageProvider;
    private final CompositeDisposable disposables;
    private SelectorProvider selectorProvider;
    private SelectionKey acceptSelectionKey;
    private final ConcurrentMap<SelectionKey, NIOConnection> connectedClientsBySelectionKey;
    private final ConcurrentMap<Connection, BlockingQueue<PostmanMessage>> messageQueueForEachClient;
    private Scheduler newThreadScheduler;


    @Inject
    ServerEventLoop(Provider<PostmanMessage> messageProvider,
                    SelectorProvider selectorProvider,
                    @Named("new") Scheduler newThreadScheduler) {
        this.messageProvider = messageProvider;
        this.selectorProvider = selectorProvider;
        this.newThreadScheduler = newThreadScheduler;
        this.connectedClientsBySelectionKey = new ConcurrentHashMap<>();
        this.messageQueueForEachClient = new ConcurrentHashMap<>();
        this.disposables = new CompositeDisposable();
    }

    void shutdownLoop() {
        disposables.clear();

        connectedClientsBySelectionKey.values().forEach(NIOConnection::destroy);

        IO.closeQuietly(serverSocketChannel);
        IO.closeQuietly(nioSelector);


        connectedClientsBySelectionKey.clear();
        messageQueueForEachClient.clear();

    }

    boolean isRunning() {
        return nonNull(serverSocketChannel) && serverSocketChannel.isOpen() && !serverSocketChannel.socket().isClosed();
    }

    //TODO handle the return value in callers
    boolean addMessageToQueue(PostmanMessage msg, Connection destination) {

        NIOConnection NIOConnection = (NIOConnection) destination;

        if (!NIOConnection.isValid()) {
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
        nioSelector.wakeup();
        return true;


    }


    Flowable<ServerEvent> startLooping(@NonNull InetSocketAddress bindAddress) {
        Logcat.d(TAG, "Initialising Server Event Loop");
        this.bindAddress = bindAddress;

        return Flowable.<ServerEvent>create(emitter -> {
            Logcat.d(TAG, "Beginning to listen to clients");
            if (!initialiseServerSocket(emitter)) {
                return;
            }

            emitter.onNext(ServerEvent.serverListening(bindAddress.getPort(), bindAddress.getHostName()));
            try {

                while (true) {
                    v(TAG, "Waiting for selector updates");
                    int channelsReady = nioSelector.select();

                    if (!nioSelector.isOpen()) {
                        break;
                    }

                    if (nioSelector.selectedKeys().isEmpty()) {
                        Logcat.w(TAG, "Selected keys are empty");
                        continue;
                    }

                    v(TAG, "%s channel(s) ready in accept loop", channelsReady);

                    processKeyUpdates(emitter);

                }

                if(!emitter.isCancelled()) {
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


    private void processKeyUpdates(FlowableEmitter<ServerEvent> emitter) {
        nioSelector.selectedKeys().forEach(selectionKey -> {
            v(TAG, "SK : valid=%b read=%b write%b accept=%b", selectionKey.isValid(), selectionKey.isReadable(), selectionKey.isWritable(), selectionKey.isAcceptable());

            NIOConnection connection = connectedClientsBySelectionKey.get(selectionKey);

            if (!selectionKey.isValid()) {

                if(nonNull(connection)) {
                    cleanupConnection(connection);
                    emitter.onNext(ServerEvent.clientDisconnected(connection));
                }
                return;
            }

            if (selectionKey.isAcceptable()) {
                acceptClientConnection(emitter);
            }

            if(selectionKey.isReadable()) {
                handleRead(selectionKey, emitter);
            }

            if(selectionKey.isWritable()) {
                handleWrite(selectionKey, emitter);
            }
        });

        nioSelector.selectedKeys().clear();
    }

    private void handleRead(SelectionKey selectionKey, FlowableEmitter<ServerEvent> emitter) {
        NIOConnection connection = connectedClientsBySelectionKey.get(selectionKey);

        try {
            connection.read();
        } catch (Exception e) {
            Logcat.e(TAG, "Lost connection", e);
            cleanupConnection(connection);
            emitter.onNext(ServerEvent.clientDisconnected(connection));
            return;
        }

        if(connection.isValid()) {
            connection.filledMessages().forEach(msg -> {
                Logcat.v(TAG, "Message received [%s]", msg.toString());
                emitter.onNext(ServerEvent.newMessage(msg, connection));
            });
        }
    }

    private void handleWrite(SelectionKey selectionKey, FlowableEmitter<ServerEvent> emitter) {
        NIOConnection connection = connectedClientsBySelectionKey.get(selectionKey);

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
                    emitter.onNext(ServerEvent.clientDisconnected(connection));
                    return;
                }

            }
        }

        if(messagesForClient.isEmpty() && connection.isValid()) {
            connection.unsetWriteInterest();
        }
    }


    private boolean initialiseServerSocket(FlowableEmitter<ServerEvent> emitter) {
        try {
            nioSelector = selectorProvider.openSelector();
            serverSocketChannel = selectorProvider.openServerSocketChannel();
            ServerSocket serverSocket = serverSocketChannel.socket();
            serverSocket.setPerformancePreferences(Connection.CONNECTION_TIME_PREFERENCE,
                    Connection.LATENCY_PREFERENCE,Connection.BANDWIDTH_PREFERENCE);
            serverSocket.bind(bindAddress);
            v(TAG, "Server bound to %s:%d", bindAddress.getAddress().getHostAddress(), serverSocket.getLocalPort());
            serverSocketChannel.configureBlocking(false);
            acceptSelectionKey = serverSocketChannel.register(nioSelector,
                    SelectionKey.OP_ACCEPT);
            return true;
        } catch (Exception e) {
            shutdownLoop();
            emitter.onError(e);
            return false;
        }
    }

    private void acceptClientConnection(FlowableEmitter<ServerEvent> emitter) {

        d(TAG, "Accepting new connection channel");
        SocketChannel clientSocketChannel = null;
        NIOConnection connection;
        SelectionKey clientKey;
        try {

            clientSocketChannel = serverSocketChannel.accept();
            clientSocketChannel.socket().setKeepAlive(true);
            clientSocketChannel.socket().setPerformancePreferences(Connection.CONNECTION_TIME_PREFERENCE,
                    Connection.LATENCY_PREFERENCE,Connection.BANDWIDTH_PREFERENCE);
            clientSocketChannel.configureBlocking(false);
            clientKey = clientSocketChannel.register(nioSelector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);
            connection = new NIOConnection(clientSocketChannel, messageProvider, clientKey);
        } catch (Throwable e) {
            w(TAG, "Couldnt accept connection channel", e);
            IO.closeQuietly(clientSocketChannel);
            return;
        }

        connectedClientsBySelectionKey.put(clientKey, connection);
        emitter.onNext(ServerEvent.newClient(connection));

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
            messageQueueForEachClient.get(client).clear();
            messageQueueForEachClient.remove(client);
        }

    }

    public Collection<NIOConnection> getClients() {
        return connectedClientsBySelectionKey.values();
    }
}