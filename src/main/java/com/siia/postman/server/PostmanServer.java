package com.siia.postman.server;

import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import com.google.protobuf.MessageLite;

import java.net.InetSocketAddress;
import java.util.UUID;

import javax.inject.Singleton;

import io.reactivex.subjects.PublishSubject;


@Singleton
public interface PostmanServer {


    @AnyThread
    void serverStart(@NonNull InetSocketAddress bindAddress);

    @WorkerThread
    void stopServer();

    @AnyThread
    boolean isRunning();

    @AnyThread
    PublishSubject<ServerEvent> getServerEventsStream();

    @AnyThread
    void broadcastMessage(PostmanMessage msg);
    @AnyThread
    void broadcastMessage(MessageLite msg);

    @AnyThread
    void sendMessage(@NonNull  PostmanMessage msg, @NonNull Connection client);

    @AnyThread
    void sendMessage(@NonNull MessageLite message, @NonNull Connection client);

    @AnyThread
    int numberOfClients();

    @WorkerThread
    void disconnectClient(@NonNull UUID uuid);

    @AnyThread
    void sendMessage(MessageLite msg, UUID uuid);
}
