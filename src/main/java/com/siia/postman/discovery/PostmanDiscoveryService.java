package com.siia.postman.discovery;


import io.reactivex.subjects.PublishSubject;

public interface PostmanDiscoveryService {

    void startServiceBroadcast(int post, String hostAddress);
    void stopServiceBroadcast();

    boolean isBroadcasting();

    PublishSubject<PostmanDiscoveryEvent> getDiscoveryEventStream();
    void discoverService();

    void stopDiscovery();
}
