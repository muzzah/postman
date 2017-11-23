package com.siia.postman.server;

import io.reactivex.Flowable;

public interface PostmanServer {

    Flowable<NetworkEvent> startServer();

    void stopServer();

    boolean isRunning();

    void getClient(int clientId);

}
