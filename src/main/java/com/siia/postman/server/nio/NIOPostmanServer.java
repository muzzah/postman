package com.siia.postman.server.nio;

import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import com.google.protobuf.MessageLite;
import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.Connection;
import com.siia.postman.server.PostmanMessage;
import com.siia.postman.server.PostmanServer;
import com.siia.postman.server.ServerEvent;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.inject.Provider;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static com.siia.commons.core.check.Check.checkState;


public class NIOPostmanServer implements PostmanServer {
    private static final String TAG = Logcat.getTag();

    private final InetSocketAddress bindAddress;
    private ServerEventLoop serverEventLoop;
    private PublishSubject<ServerEvent> serverEventsStream;
    private final ConcurrentMap<UUID, Connection> clients;
    private final Provider<PostmanMessage> messageProvider;
    private final CompositeDisposable disposables;

    public NIOPostmanServer(Provider<PostmanMessage> messageProvider) {
        this.messageProvider = messageProvider;
        this.disposables = new CompositeDisposable();
        this.bindAddress = new InetSocketAddress("0.0.0.0", 8089);
        this.serverEventsStream = PublishSubject.create();
        this.clients = new ConcurrentHashMap<>();
    }

    @Override
    public PublishSubject<ServerEvent> getServerEventsStream() {
        return serverEventsStream;
    }

    @Override
    public void broadcastMessage(PostmanMessage msg) {
        clients.values().stream().parallel().forEach(client -> serverEventLoop.getMessageQueue().addMessageToQueue(msg, client));
    }

    @Override
    public void broadcastMessage(MessageLite msg) {
        broadcastMessage(new PostmanMessage(msg));
    }

    @Override
    public void sendMessage(@NonNull PostmanMessage msg, @NonNull Connection client) {
        serverEventLoop.getMessageQueue().addMessageToQueue(msg, client);
    }

    @Override
    public void sendMessage(@NonNull MessageLite msg, @NonNull Connection client) {
        serverEventLoop.getMessageQueue().addMessageToQueue(new PostmanMessage(msg), client);
    }

    @Override
    public int numberOfClients() {
        return clients.size();
    }

    @Override
    @WorkerThread
    public void disconnectClient(@NonNull UUID uuid) {
        throw new UnsupportedOperationException("Need to impelment this still");
    }

    @Override
    public void serverStart() {
        checkState(!isRunning(), "Server is already running");

        serverEventLoop = new ServerEventLoop(bindAddress, Schedulers.computation(), messageProvider);


        Disposable eventDisposable = serverEventLoop.getServerEventsStream()
                .observeOn(Schedulers.computation())
                .filter(serverEvent -> !serverEvent.isNewMessage())
                .subscribe(
                        event -> {
                            switch (event.type()) {
                                case CLIENT_JOIN:
                                    Logcat.d(TAG, "Client connected [%s]", event.connection().getConnectionId());
                                    clients.put(event.connectionId(), event.connection());
                                    serverEventsStream.onNext(ServerEvent.newClient(event.connection(), clients.size()));
                                    break;
                                case CLIENT_DISCONNECT:
                                    Logcat.d(TAG, "Client disconnected [%s]", event.connection().getConnectionId());
                                    clients.remove(event.connectionId());
                                    serverEventsStream.onNext(event);
                                    break;
                                case SERVER_LISTENING:
                                    serverEventsStream.onNext(event);
                                    break;
                                default:
                                    Logcat.d(TAG, "Not processing event %s", event.type().name());
                                    break;
                            }


                        },
                        error -> serverEventsStream.onError(new UnexpectedServerShutdownException(error)),
                        () -> serverEventsStream.onComplete());


        Disposable newMessageDisposable = serverEventLoop.getServerEventsStream()
                .observeOn(Schedulers.computation())
                .filter(serverEvent -> serverEvent.isNewMessage() && clients.containsKey(serverEvent.connectionId()))
                .subscribe(
                        event -> serverEventsStream.onNext(ServerEvent.newMessage(event.message(), event.connection()))
                );

        disposables.add(newMessageDisposable);
        disposables.add(eventDisposable);

        serverEventLoop.startLooping();

    }

    @Override
    @WorkerThread
    public void stopServer() {
        checkState(isRunning(), "Server is not running");
        if (serverEventLoop != null) {
            serverEventLoop.shutdownLoop();
        }
        clients.clear();
    }

    @Override
    public boolean isRunning() {
        return serverEventLoop != null
                && serverEventLoop.isRunning();

    }


    private class UnexpectedServerShutdownException extends Throwable {
        UnexpectedServerShutdownException(Throwable error) {
            super(error);
        }
    }
}
