package com.siia.postman.server;

import io.reactivex.subjects.PublishSubject;


public interface PostmanServer {

    void serverStart();

    void stopServer();

    boolean isRunning();

    PublishSubject<ServerEvent> getServerEventsStream();

    void broadcastMessage(PostmanMessage msg);

    void sendMessage(PostmanMessage msg, Connection client);

    String getId();

    int numberOfClients();
}
