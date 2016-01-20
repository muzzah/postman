package com.siia.postman.server;

public interface Server {

    void startServer(NetworkEventListener listener);

    void stopServer();

    void getClient(int clientId);
}
