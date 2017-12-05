package com.siia.postman.server;


import java.util.UUID;

import io.reactivex.subjects.PublishSubject;

public interface PostmanClient {

    PublishSubject<PostmanClientEvent> getClientEventStream();

    void connect(String host, int port);

    void sendMessage(PostmanMessage msg);

    void disconnect();

    boolean isConnected();

    UUID getClientId();
}
