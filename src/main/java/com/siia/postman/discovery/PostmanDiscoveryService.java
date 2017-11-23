package com.siia.postman.discovery;


public interface PostmanDiscoveryService {

    void startServiceBroadcast(int post, String hostAddress);
    void stopServiceBroadcast();
    void startServiceDiscovery();
    void stopServiceDiscovery();
}
