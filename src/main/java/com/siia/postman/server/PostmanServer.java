package com.siia.postman.server;

public interface PostmanServer {

    void startServer(NetworkEventListener listener);

    void stopServer();

    boolean isRunning();

    void getClient(int clientId);

    void discoverServerService();
    void cancelDiscovery();
}
