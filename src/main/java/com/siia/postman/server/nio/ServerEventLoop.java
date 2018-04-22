package com.siia.postman.server.nio;

import android.annotation.SuppressLint;
import android.util.Log;

import com.siia.commons.core.io.IO;
import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.Connection;
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
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;

import static com.siia.commons.core.log.Logcat.d;
import static com.siia.commons.core.log.Logcat.v;
import static com.siia.commons.core.log.Logcat.w;

class ServerEventLoop {
    private static final String TAG = Logcat.getTag();

    private ServerSocketChannel serverSocketChannel;
    private Selector clientJoinSelector;
    private MessageQueueLoop messageRouter;
    private final InetSocketAddress bindAddress;
    private final Scheduler computation;
    private final Provider<PostmanMessage> messageProvider;
    private final FlowableProcessor<ServerEvent> serverEventStream;
    private final CompositeDisposable disposables;
    private final Scheduler io;
    private final Scheduler newThreadScheduler;

    ServerEventLoop(InetSocketAddress bindAddress, Scheduler computation, Provider<PostmanMessage> messageProvider, Scheduler io,
                    Scheduler newThreadScheduler) {
        this.bindAddress = bindAddress;
        this.computation = computation;
        this.messageProvider = messageProvider;
        this.io = io;
        this.newThreadScheduler = newThreadScheduler;
        serverEventStream = PublishProcessor.<ServerEvent>create().toSerialized();
        disposables = new CompositeDisposable();
    }

    void shutdownLoop() {
        messageRouter.stopLoop();
        disposables.clear();
        IO.closeQuietly(serverSocketChannel);
        IO.closeQuietly(clientJoinSelector);
        messageRouter.shutdown();

    }

    boolean isRunning() {
        return serverSocketChannel != null && serverSocketChannel.isOpen() && !serverSocketChannel.socket().isClosed();
    }


    FlowableProcessor<ServerEvent> getServerEventsStream() {
        return serverEventStream;

    }

    @SuppressLint("CheckResult")
    void startLooping() {
        Logcat.d(TAG, "Initialising Server Event Loop");
        messageRouter = new MessageQueueLoop(newThreadScheduler);
        disposables.add(messageRouter.messageQueueEventsStream()
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


        messageRouter.startMessageQueueLoop()
                .observeOn(io)
                .subscribe(
                        readyMsg -> startListeningForClients(),
                        error -> {
                            shutdownLoop();
                            serverEventStream.onError(error);
                        },
                        () -> Logcat.i(TAG, "Message Queue completed"));

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

    private void startListeningForClients() {
        Logcat.d(TAG, "Beginning to listen to clients");
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
            serverSocket.setPerformancePreferences(Connection.CONNECTION_TIME_PREFERENCE,
                    Connection.LATENCY_PREFERENCE,Connection.BANDWIDTH_PREFERENCE);
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
            clientSocketChannel.socket().setKeepAlive(true);
            clientSocketChannel.socket().setPerformancePreferences(Connection.CONNECTION_TIME_PREFERENCE,
                    Connection.LATENCY_PREFERENCE,Connection.BANDWIDTH_PREFERENCE);
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