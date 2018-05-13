package com.siia.postman.server;

import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;

import com.google.protobuf.MessageLite;

import java.net.InetSocketAddress;
import java.util.UUID;

import io.reactivex.Flowable;


public interface PostmanServer {


    @AnyThread
    Flowable<PostmanServerEvent> serverStart(@NonNull InetSocketAddress bindAddress);

    @AnyThread
    void stopServer();

    @AnyThread
    boolean isRunning();

    @AnyThread
    void broadcastMessage(MessageLite msg);

    @AnyThread
    void sendMessage(@NonNull  PostmanMessage msg, @NonNull Connection client);

    @AnyThread
    void sendMessage(@NonNull MessageLite message, @NonNull Connection client);

    @AnyThread
    int numberOfClients();

    @AnyThread
    void sendMessage(MessageLite msg, UUID uuid);
}
