package com.siia.postman.server.nio;

import android.annotation.SuppressLint;
import android.util.Log;

import com.siia.commons.core.io.IO;
import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.PostmanMessage;
import com.siia.postman.server.PostmanServerClient;
import com.siia.postman.server.ServerEvent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.UUID;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static com.siia.commons.core.log.Logcat.d;
import static com.siia.commons.core.log.Logcat.i;
import static com.siia.commons.core.log.Logcat.v;
import static com.siia.commons.core.log.Logcat.w;

class ServerEventLoop {
    private static final String TAG = Logcat.getTag();

    private ServerSocketChannel serverSocketChannel;
    private Selector clientJoinSelector;
    private final InetSocketAddress bindAddress;
    private MessageQueueLoop messageRouter;
    private PublishSubject<ServerEvent> serverEventStream;

    @SuppressLint("UseSparseArrays")
    ServerEventLoop(InetSocketAddress bindAddress) {
        this.bindAddress = bindAddress;
        this.messageRouter = new MessageQueueLoop();
        serverEventStream = PublishSubject.create();

    }

    void shutdownLoop() {

        IO.closeQuietly(clientJoinSelector);

        IO.closeQuietly(serverSocketChannel.socket());
        IO.closeQuietly(serverSocketChannel);

        messageRouter.shutdown();
    }

    boolean isRunning() {
        return serverSocketChannel != null && serverSocketChannel.isOpen() && !serverSocketChannel.socket().isClosed();
    }


    Observable<ServerEvent> getServerEventsStream() {
        return serverEventStream;

    }

    void startLooping() {

        messageRouter.messageRouterEventStream()
                .observeOn(Schedulers.computation())
                .subscribe(
                        event -> {
                            switch (event.type()) {
                                case CLIENT_REGISTERED:
                                    serverEventStream.onNext(ServerEvent.newClient(event.client()));
                                    break;
                                case CLIENT_REGISTRATION_FAILED:
                                    Log.w(TAG, "Problem when registering client");
                                    break;
                                case CLIENT_UNREGISTERED:
                                    serverEventStream.onNext(ServerEvent.clientDiscommected(event.client()));
                                    break;
                                default:
                                    throw new UnsupportedOperationException("Unhandled event type from server message router");
                            }

                        },
                        error -> {
                            Log.e(TAG, "Message router ended unexpectedly");
                            shutdownLoop();
                            serverEventStream.onError(error);
                        },
                        () -> {
                            Logcat.i(TAG, "Message router loop ended");
                        }
                );

        Completable.<PostmanServerClient>create(flowableEmitter -> {


            try {
                initialiseServerSocket();
            } catch (IOException error) {
                flowableEmitter.onError(error);
                return;
            }

            try {
                messageRouter.startMessageQueueLoop();
                serverEventStream.onNext(ServerEvent.serverListening(bindAddress.getPort(), bindAddress.getHostName()));

                while (true) {
                    v(TAG, "Waiting for incoming connections");
                    int channelsReady = clientJoinSelector.select();

                    if (!clientJoinSelector.isOpen()) {
                        break;
                    }

                    if (clientJoinSelector.selectedKeys().isEmpty()) {
                        Log.w(TAG, "Selected keys are empty");
                        continue;
                    }

                    v(TAG, "%s channel(s) ready in accept loop", channelsReady);

                    processKeyUpdates();

                }
                flowableEmitter.onComplete();
            } catch (Exception e) {
                flowableEmitter.onError(e);
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .subscribe(
                        () -> {
                            serverEventStream.onComplete();
                        },
                        error -> {
                            Logcat.e(TAG, "Error received %s", error);
                            shutdownLoop();
                            serverEventStream.onError(error);
                        });

    }

    private void processKeyUpdates() {
        clientJoinSelector.selectedKeys().forEach(selectionKey -> {
            v(TAG, "clientJoin SK : v=%s a=%s", selectionKey.isValid(), selectionKey.isAcceptable());
            if (!selectionKey.isValid()) {
                return;
            }

            if (selectionKey.isAcceptable()) {

                ServerClient client = acceptClientConnection();

                if (client != null) {
                    messageRouter.addClient(client);
                }
            } else {
                Log.w(TAG, "Unrecognised interest operation for server socket channel");
            }
        });

        clientJoinSelector.selectedKeys().clear();
    }


    private void initialiseServerSocket() throws IOException {
        clientJoinSelector = Selector.open();
        serverSocketChannel = ServerSocketChannel.open();
        ServerSocket serverSocket = serverSocketChannel.socket();
        serverSocket.bind(bindAddress);
        v(TAG, "Server bound to %s:%d", bindAddress.getAddress().getHostAddress(), serverSocket.getLocalPort());
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(clientJoinSelector,
                SelectionKey.OP_ACCEPT);
    }

    private ServerClient acceptClientConnection() {
        d(TAG, "Accepting new client channel");
        SocketChannel clientSocketChannel = null;
        try {
            clientSocketChannel = serverSocketChannel.accept();
            clientSocketChannel.configureBlocking(false);

        } catch (IOException e) {
            w(TAG, "Couldnt accept client channel", e);
            IO.closeQuietly(clientSocketChannel);
            return null;
        }
        return new ServerClient(UUID.randomUUID(), clientSocketChannel);

    }

    void addMessageToQueue(PostmanMessage postmanMessage, PostmanServerClient client) {
        messageRouter.addMessageToQueue(postmanMessage, client);
    }
}