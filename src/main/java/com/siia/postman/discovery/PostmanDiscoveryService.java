package com.siia.postman.discovery;


import javax.inject.Singleton;

import io.reactivex.subjects.PublishSubject;

@Singleton
public interface PostmanDiscoveryService {

    void startServiceBroadcast(int post, String hostAddress);
    void stopServiceBroadcast();

    boolean isBroadcasting();

    PublishSubject<PostmanDiscoveryEvent> getDiscoveryEventStream();
    void discoverService();

    void stopDiscovery();
}
