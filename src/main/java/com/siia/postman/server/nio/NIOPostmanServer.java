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

import io.reactivex.Scheduler;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;

import static java.util.Objects.nonNull;


public class NIOPostmanServer implements PostmanServer {
    private static final String TAG = Logcat.getTag();

    private ServerEventLoop serverEventLoop;
    private FlowableProcessor<ServerEvent> serverEventsStream;
    private final ConcurrentMap<UUID, Connection> clients;
    private final Provider<PostmanMessage> messageProvider;
    private final CompositeDisposable disposables;
    private final Scheduler computation;
    private final Scheduler io;
    private final Scheduler newThreadScheduler;

    public NIOPostmanServer(Provider<PostmanMessage> messageProvider, Scheduler computation, Scheduler io, Scheduler newThreadScheduler) {
        this.messageProvider = messageProvider;
        this.computation = computation;
        this.io = io;
        this.newThreadScheduler = newThreadScheduler;
        this.disposables = new CompositeDisposable();
        this.clients = new ConcurrentHashMap<>();
        this.serverEventsStream = PublishProcessor.<ServerEvent>create().toSerialized();
    }

    @Override
    public FlowableProcessor<ServerEvent> getServerEventsStream() {
        return serverEventsStream;
    }

    @Override
    public void broadcastMessage(@NonNull MessageLite msg) {
        clients.values().stream().parallel().forEach(client -> serverEventLoop.getMessageQueue().addMessageToQueue(new PostmanMessage(msg), client));
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
    public void sendMessage(MessageLite msg, UUID uuid) {
        Connection connection = clients.get(uuid);
        if (connection == null) {
            Logcat.w(TAG, "Client %s does not seem to be connected, not sending message", uuid.toString());
            return;
        }
        serverEventLoop.getMessageQueue().addMessageToQueue(new PostmanMessage(msg), connection);
    }

    @Override
    public void serverStart(@NonNull InetSocketAddress bindAddress) {
        if (isRunning()) {
            Logcat.w(TAG, "Server already running");
            return;
        }

        Logcat.d(TAG, "Starting postman server");
        serverEventLoop = new ServerEventLoop(bindAddress, computation, messageProvider, io, newThreadScheduler);

        Disposable eventDisposable = serverEventLoop.getServerEventsStream()
                .observeOn(computation)
                .filter(serverEvent -> !serverEvent.isNewMessage())
                .subscribe(
                        event -> {
                            Logcat.v(TAG, "Server event received %s", event.type());
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
                        //TODO Separate out the internal server error streams from event streams so that we can distinguish and handle them seprately
                        //i.e clients who susbscribe to the outgoing event stream can handle errors when they should be hidden from them.
                        // Right now they can because we send internal server errors
                        //Down stream
                        error -> serverEventsStream.onError(new UnexpectedServerShutdownException(error)),
                        () -> serverEventsStream.onComplete());


        Disposable newMessageDisposable = serverEventLoop.getServerEventsStream()
                .observeOn(computation)
                .filter(serverEvent -> serverEvent.isNewMessage() && clients.containsKey(serverEvent.connectionId()))
                .subscribe(
                        event -> {
                            Logcat.v(TAG, "Msg Stream hasSubscriber=%b isComplete=%b hasThrowable=%b",
                                    serverEventsStream.hasSubscribers(),
                                    serverEventsStream.hasComplete(),
                                    serverEventsStream.hasThrowable());

                            serverEventsStream.onNext(ServerEvent.newMessage(event.message(), event.connection()));
                        }
                );

        disposables.add(newMessageDisposable);
        disposables.add(eventDisposable);

        serverEventLoop.startLooping();

    }

    @Override
    @WorkerThread
    public void stopServer() {
        if (!isRunning()) {
            Logcat.w(TAG, "Server is not running");
            return;
        }

        if (nonNull(serverEventLoop)) {
            serverEventLoop.shutdownLoop();
        }

        disposables.clear();


        clients.clear();
    }

    @Override
    public boolean isRunning() {
        return nonNull(serverEventLoop)
                && serverEventLoop.isRunning();
    }


    private class UnexpectedServerShutdownException extends Throwable {
        UnexpectedServerShutdownException(Throwable error) {
            super(error);
        }
    }
}
