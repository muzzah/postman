package com.siia.postman.server.nio;

import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.ServerClientAuthenticator;
import com.siia.postman.server.PostmanMessage;
import com.siia.postman.server.PostmanServer;
import com.siia.postman.server.Connection;
import com.siia.postman.server.ServerEvent;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static com.siia.commons.core.check.Check.checkState;


public class NIOPostmanServer implements PostmanServer {
    private static final String TAG = Logcat.getTag();

    private final InetSocketAddress bindAddress;
    private ServerEventLoop serverEventLoop;
    private PublishSubject<ServerEvent> serverEventsStream;
    private Disposable clientJoinDisposable;
    private final List<Connection> clients;
    private final UUID id;

    public NIOPostmanServer() {
        this.bindAddress = new InetSocketAddress("0.0.0.0", 8888);
        this.serverEventsStream = PublishSubject.create();
        this.clients = Collections.synchronizedList(new ArrayList<>(20));
        this.id = UUID.randomUUID();

    }


    @Override
    public PublishSubject<ServerEvent> getServerEventsStream() {
        return serverEventsStream;
    }

    @Override
    public void broadcastMessage(PostmanMessage msg) {
        clients.parallelStream().forEach(client -> {
            serverEventLoop.getMessageQueue().addMessageToQueue(msg, client);
        });
    }

    @Override
    public void sendMessage(PostmanMessage msg, Connection client) {
        serverEventLoop.getMessageQueue().addMessageToQueue(msg, client);
    }

    @Override
    public String getId() {
        return id.toString();
    }

    @Override
    public void serverStart() {
        checkState(!isRunning(), "Server is already running");

        serverEventLoop = new ServerEventLoop(bindAddress, Schedulers.computation());


        clientJoinDisposable = serverEventLoop.getServerEventsStream()
                .observeOn(Schedulers.computation())
                .doOnSubscribe(clientJoinDisposable -> serverEventLoop.startLooping())
                .subscribe(
                        event -> {
                            switch (event.type()) {
                                case CLIENT_JOIN:
                                    Logcat.i(TAG, "Client connected [%s]", event.client().getClientId());
                                    clients.add(event.client());
                                    ServerClientAuthenticator handler = new ServerClientAuthenticator(this,
                                            serverEventLoop.getServerEventsStream(),
                                            event.client());
                                    handler.beginAuthentication();
                                    break;
                                case CLIENT_DISCONNECT:
                                    Logcat.i(TAG, "Client disconnected [%s]", event.client().getClientId());
                                    clients.remove(event.client());
                                    break;
                                case SERVER_LISTENING:
                                    serverEventsStream.onNext(event);
                                    break;
                                default:
                                    Logcat.d(TAG, "Not processing event %s", event.type().name());
                                    break;
                            }


                        },
                        error -> {
                            Logcat.e(TAG, "Error on server events stream", error);
                            clientJoinDisposable.dispose();
                            this.serverEventsStream.onError(error);
                        },
                        () -> {
                            Logcat.i(TAG, "Server Events stream has ended");
                            clientJoinDisposable.dispose();
                            this.serverEventsStream.onComplete();
                        });


    }

    @Override
    public void stopServer() {
        checkState(isRunning(), "Server is not running");
        if (serverEventLoop != null) {
            serverEventLoop.shutdownLoop();
        }
    }

    @Override
    public boolean isRunning() {
        return serverEventLoop != null
                && serverEventLoop.isRunning();

    }


}
