package com.siia.postman.server.nio;

import android.util.Log;

import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.Connection;
import com.siia.postman.server.PostmanMessage;
import com.siia.postman.server.PostmanServer;
import com.siia.postman.server.ServerClientAuthenticator;
import com.siia.postman.server.ServerEvent;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;

import javax.inject.Provider;

import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static com.siia.commons.core.check.Check.checkState;


public class NIOPostmanServer implements PostmanServer {
    private static final String TAG = Logcat.getTag();

    private final InetSocketAddress bindAddress;
    private ServerEventLoop serverEventLoop;
    private PublishSubject<ServerEvent> serverEventsStream;
    private Disposable eventDisposable;
    private final ConcurrentSkipListSet<Connection> clients;
    private final UUID id;
    private Disposable newMessageDisposable;
    private final Provider<PostmanMessage> messageProvider;

    public NIOPostmanServer(Provider<PostmanMessage> messageProvider) {
        this.messageProvider = messageProvider;
        this.bindAddress = new InetSocketAddress("0.0.0.0", 8889);
        this.serverEventsStream = PublishSubject.create();
        this.clients = new ConcurrentSkipListSet<>();
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

        serverEventLoop = new ServerEventLoop(bindAddress, Schedulers.computation(), messageProvider);


        eventDisposable = serverEventLoop.getServerEventsStream()
                .observeOn(Schedulers.computation())
                .filter(serverEvent -> !serverEvent.isNewMessage())
                .subscribe(
                        event -> {
                            switch (event.type()) {
                                case CLIENT_JOIN:
                                    Logcat.i(TAG, "Client connected [%s]", event.connection().getConnectionId());
                                    ServerClientAuthenticator handler = new ServerClientAuthenticator(this,
                                            serverEventLoop.getServerEventsStream(),
                                            event.connection());

                                    handler.beginAuthentication().observeOn(Schedulers.computation())
                                            .subscribe(
                                                    state -> {
                                                        switch (state) {
                                                            case AUTHENTICATED:
                                                                clients.add(event.connection());
                                                                serverEventsStream.onNext(ServerEvent.newClient(event.connection()));
                                                                break;
                                                            case AUTH_FAILED:
                                                                Log.w(TAG, "Auth failed");
                                                                break;
                                                        }
                                                    });

                                    break;
                                case CLIENT_DISCONNECT:
                                    Logcat.i(TAG, "Client disconnected [%s]", event.connection().getConnectionId());
                                    clients.remove(event.connection());
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
                            serverEventsStream.onError(error);
                        },
                        () -> {
                            Logcat.i(TAG, "Server Events stream has ended");
                            serverEventsStream.onComplete();
                        });


        newMessageDisposable = serverEventLoop.getServerEventsStream()
                .observeOn(Schedulers.computation())
                .filter(ServerEvent::isNewMessage)
                .subscribe(
                        event -> {
                            if (clients.contains(event.connection())) {
                                serverEventsStream.onNext(ServerEvent.newMessage(event.message(), event.connection()));
                            }
                        }
                );

        serverEventLoop.startLooping();

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
