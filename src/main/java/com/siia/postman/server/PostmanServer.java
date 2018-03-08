package com.siia.postman.server;

import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import java.util.UUID;

import io.reactivex.subjects.PublishSubject;


public interface PostmanServer {


    void serverStart();

    @WorkerThread
    void stopServer();

    boolean isRunning();

    PublishSubject<ServerEvent> getServerEventsStream();

    void broadcastMessage(PostmanMessage msg);

    void sendMessage(@NonNull  PostmanMessage msg, @NonNull Connection client);

    int numberOfClients();

    @WorkerThread
    void disconnectClient(@NonNull UUID uuid);
}
