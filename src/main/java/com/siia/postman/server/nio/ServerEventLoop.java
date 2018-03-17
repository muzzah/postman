package com.siia.postman.server.nio;

import android.util.Log;

import com.siia.commons.core.io.IO;
import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.PostmanMessage;
import com.siia.postman.server.ServerEvent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import javax.inject.Provider;

import io.reactivex.Scheduler;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static com.siia.commons.core.log.Logcat.d;
import static com.siia.commons.core.log.Logcat.v;
import static com.siia.commons.core.log.Logcat.w;

class ServerEventLoop {
    private static final String TAG = Logcat.getTag();

    private ServerSocketChannel serverSocketChannel;
    private Selector clientJoinSelector;
    private final InetSocketAddress bindAddress;
    private final Scheduler computation;
    private final Provider<PostmanMessage> messageProvider;
    private final MessageQueueLoop messageRouter;
    private final PublishSubject<ServerEvent> serverEventStream;
    private final CompositeDisposable disposables;

    ServerEventLoop(InetSocketAddress bindAddress, Scheduler computation, Provider<PostmanMessage> messageProvider) {
        this.bindAddress = bindAddress;
        this.computation = computation;
        this.messageProvider = messageProvider;
        this.messageRouter = new MessageQueueLoop();
        serverEventStream = PublishSubject.create();

        disposables = new CompositeDisposable();
    }

    void shutdownLoop() {

        disposables.clear();
        IO.closeQuietly(clientJoinSelector);
        IO.closeQuietly(serverSocketChannel.socket());
        IO.closeQuietly(serverSocketChannel);
        messageRouter.shutdown();

    }

    boolean isRunning() {
        return serverSocketChannel != null && serverSocketChannel.isOpen() && !serverSocketChannel.socket().isClosed();
    }


    PublishSubject<ServerEvent> getServerEventsStream() {
        return serverEventStream;

    }

    void startLooping() {

        disposables.add(messageRouter.messageRouterEventStream()
                .observeOn(computation)
                .subscribe(
                        this::handleMessageQueueEvent,
                        error -> {
                            shutdownLoop();
                            serverEventStream.onError(error);
                        },
                        () -> {
                            shutdownLoop();
                            serverEventStream.onComplete();
                        }
                ));


        disposables.add(messageRouter.messageRouterEventStream().observeOn(Schedulers.io())
                .filter(messageQueueEvent -> messageQueueEvent.type().equals(MessageQueueEvent.Type.READY))
                .doOnSubscribe(disposable -> messageRouter.startMessageQueueLoop())
                .subscribe(
                        this::startListeningForClients,
                        this::handleMessageQueueError));


    }

    private void handleMessageQueueError(Throwable throwable) {
        Logcat.e(TAG, "Error in Message", throwable);
    }


    private void handleMessageQueueEvent(MessageQueueEvent messageQueueEvent){
        switch (messageQueueEvent.type()) {
            case CLIENT_REGISTERED:
                serverEventStream.onNext(ServerEvent.newClient(messageQueueEvent.client()));
                break;
            case CLIENT_REGISTRATION_FAILED:
                Log.w(TAG, "Problem when registering connection");
                break;
            case CLIENT_UNREGISTERED:
                serverEventStream.onNext(ServerEvent.clientDisconnected(messageQueueEvent.client()));
                break;
            case MESSAGE:
                serverEventStream.onNext(ServerEvent.newMessage(messageQueueEvent.msg(), messageQueueEvent.client()));
                break;
            case READY:
                break;
            default:
                throw new UnsupportedOperationException("Unhandled event type from server message router");
        }

    }

    private void startListeningForClients(@SuppressWarnings("unused") MessageQueueEvent messageQueueEvent) {
        if (!initialiseServerSocket()) {
            serverEventStream.onError(new SocketException("Could not initialise server socket"));
            return;
        }

        try {
            serverEventStream.onNext(ServerEvent.serverListening(bindAddress.getPort(), bindAddress.getHostName()));

            while (true) {
                v(TAG, "Waiting for selector updates");
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
            shutdownLoop();
            serverEventStream.onComplete();
        } catch (Exception e) {
            serverEventStream.onError(e);
        }
    }

    private void processKeyUpdates() {
        clientJoinSelector.selectedKeys().forEach(selectionKey -> {
            v(TAG, "clientJoin SK : v=%s a=%s", selectionKey.isValid(), selectionKey.isAcceptable());
            if (!selectionKey.isValid()) {
                return;
            }

            if (selectionKey.isAcceptable()) {

                NIOConnection client = acceptClientConnection();

                if (client != null) {
                    messageRouter.addClient(client);
                }
            } else {
                Log.w(TAG, "Unrecognised interest operation for server socket channel");
            }
        });

        clientJoinSelector.selectedKeys().clear();
    }


    private boolean initialiseServerSocket() {
        try {
            clientJoinSelector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            ServerSocket serverSocket = serverSocketChannel.socket();
            serverSocket.bind(bindAddress);
            v(TAG, "Server bound to %s:%d", bindAddress.getAddress().getHostAddress(), serverSocket.getLocalPort());
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(clientJoinSelector,
                    SelectionKey.OP_ACCEPT);
            return true;
        } catch (Exception e) {
            shutdownLoop();
            serverEventStream.onError(e);
            return false;
        }
    }

    private NIOConnection acceptClientConnection() {

        d(TAG, "Accepting new connection channel");
        SocketChannel clientSocketChannel = null;
        try {
            clientSocketChannel = serverSocketChannel.accept();
            clientSocketChannel.configureBlocking(false);

        } catch (IOException e) {
            w(TAG, "Couldnt accept connection channel", e);
            IO.closeQuietly(clientSocketChannel);
            return null;
        }
        return new NIOConnection(clientSocketChannel, messageProvider);

    }

    MessageQueueLoop getMessageQueue() {
        return messageRouter;
    }


}