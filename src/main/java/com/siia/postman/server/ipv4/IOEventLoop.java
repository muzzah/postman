package com.siia.postman.server.ipv4;

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

class IOEventLoop {
    private static final String TAG = Logcat.getTag();

    private ServerSocketChannel serverSocketChannel;
    private Selector clientJoinSelector;
    private final InetSocketAddress bindAddress;
    private IPIOMessageRouter messageRouter;
    private PublishSubject<ServerEvent> serverEventStream;

    @SuppressLint("UseSparseArrays")
    IOEventLoop(InetSocketAddress bindAddress) {
        this.bindAddress = bindAddress;
        this.messageRouter = new IPIOMessageRouter();
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
                .map(
                        ipMessageRouterEvent -> {
                            switch (ipMessageRouterEvent.type()) {
                                case CLIENT_REGISTERED:
                                    return ServerEvent.newClient(ipMessageRouterEvent.client());
                                case CLIENT_UNREGISTERED:
                                    return ServerEvent.clientDiscommected(ipMessageRouterEvent.client());
                                default:
                                    throw new UnsupportedOperationException("Unhandled event type from server message router");
                            }
                        })
                .subscribe(
                        event -> serverEventStream.onNext(event),
                        error -> Log.e(TAG, "Message router ended unexpectedly", error),
                        () -> Logcat.i(TAG, "Message router loop ended")
                );

        Completable.<PostmanServerClient>create(flowableEmitter -> {


            try {
                initialiseServerSocket();
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

                    clientJoinSelector.selectedKeys().forEach(selectionKey -> {
                        v(TAG, "clientJoin SK : v=%s a=%s", selectionKey.isValid(), selectionKey.isAcceptable());
                        if (!selectionKey.isValid()) {
                            return;
                        }

                        if (selectionKey.isAcceptable()) {

                            IPPostmanServerClient client = acceptClientConnection();

                            if (client != null) {
                                messageRouter.addClient(client);
                            }
                        } else {
                            Log.w(TAG, "Unrecognised interest operation for server socket channel");
                        }
                    });

                    clientJoinSelector.selectedKeys().clear();

                }
                d(TAG, "Join Event Loop exited");
                serverEventStream.onComplete();
            } catch (Exception e) {
                serverEventStream.onError(e);
            } finally {
                shutdownLoop();
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .subscribe(() -> Logcat.d(TAG, "Client Event setup finished"),
                        error -> {
                            Logcat.e(TAG, "Error received %s", error);
                            //TODO Review error handling and propogate error here
                        });

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

    private IPPostmanServerClient acceptClientConnection() {
        i(TAG, "Accepting new client channel");
        SocketChannel clientSocketChannel = null;
        try {
            clientSocketChannel = serverSocketChannel.accept();
            clientSocketChannel.configureBlocking(false);

        } catch (IOException e) {
            w(TAG, "Couldnt accept client channel", e);
            IO.closeQuietly(clientSocketChannel);
            return null;
        }
        return new IPPostmanServerClient(UUID.randomUUID(), clientSocketChannel);

    }

    void addMessageToQueue(PostmanMessage postmanMessage, PostmanServerClient client) {
        messageRouter.addMessageToQueue(postmanMessage, client);
    }
}