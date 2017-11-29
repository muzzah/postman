package com.siia.postman.server;


import io.reactivex.subjects.PublishSubject;

public interface PostmanClient {

    PublishSubject<PostmanClientEvent> getClientEventStream();

    void connect(String host, int port);

    void disconnect();

    boolean isConnected();
}
