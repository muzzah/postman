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
import java.util.Optional;
import java.util.UUID;

import io.reactivex.Flowable;
import io.reactivex.disposables.CompositeDisposable;


public class NIOPostmanServer implements PostmanServer {
    private static final String TAG = Logcat.getTag();

    private final ServerEventLoop serverEventLoop;
    private final CompositeDisposable disposables;

    public NIOPostmanServer(ServerEventLoop serverEventLoop) {
        this.serverEventLoop = serverEventLoop;
        this.disposables = new CompositeDisposable();
    }

    @Override
    public void broadcastMessage(@NonNull MessageLite msg) {
        serverEventLoop.getClients().forEach(client -> serverEventLoop.addMessageToQueue(new PostmanMessage(msg), client));
    }

    @Override
    public void sendMessage(@NonNull PostmanMessage msg, @NonNull Connection client) {
        serverEventLoop.addMessageToQueue(msg, client);
    }

    @Override
    public void sendMessage(@NonNull MessageLite msg, @NonNull Connection client) {
        serverEventLoop.addMessageToQueue(new PostmanMessage(msg), client);
    }

    @Override
    public int numberOfClients() {
        return serverEventLoop.getClients().size();
    }

    @Override
    @WorkerThread
    public void disconnectClient(@NonNull UUID uuid) {
        //TODO
        throw new UnsupportedOperationException("Need to impelment this still");
    }

    @Override
    public void sendMessage(MessageLite msg, UUID connectionId) {
        Optional<NIOConnection> connection = serverEventLoop.getClients()
                .parallelStream()
                .filter(nioConnection -> nioConnection.getConnectionId().equals(connectionId))
                .findFirst();

        if (!connection.isPresent()) {
            Logcat.w(TAG, "Client %s does not seem to be connected, not sending message", connectionId.toString());
            return;
        }
        serverEventLoop.addMessageToQueue(new PostmanMessage(msg), connection.get());
    }

    @Override
    public Flowable<ServerEvent> serverStart(@NonNull InetSocketAddress bindAddress) {
        if (isRunning()) {
            Logcat.w(TAG, "Server already running");
            return Flowable.error(new IllegalStateException("Already running"));
        }

        Logcat.d(TAG, "Starting postman server");
        return serverEventLoop.startLooping(bindAddress);

    }

    @Override
    @WorkerThread
    public void stopServer() {
        if (!isRunning()) {
            Logcat.w(TAG, "Server is not running");
            return;
        }

        serverEventLoop.shutdownLoop();

        disposables.clear();

    }

    @Override
    public boolean isRunning() {
        return serverEventLoop.isRunning();
    }


}
