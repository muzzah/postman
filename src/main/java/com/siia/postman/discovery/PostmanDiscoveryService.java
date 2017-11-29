package com.siia.postman.discovery;


import io.reactivex.Flowable;

public interface PostmanDiscoveryService {

    void startServiceBroadcast(int post, String hostAddress);
    void stopServiceBroadcast();
    Flowable<PostmanDiscoveryEvent> discoverService();
    boolean isBroadcasting();
}
