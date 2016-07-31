package com.siia.postman.server;

public interface Server {

    void startServer(NetworkEventListener listener);

    void stopServer();

    boolean isRunning();

    void getClient(int clientId);
}
