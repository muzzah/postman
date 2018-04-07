package com.siia.postman.discovery;


import android.support.annotation.NonNull;

import javax.inject.Singleton;

import io.reactivex.subjects.PublishSubject;

@Singleton
public interface PostmanDiscoveryService {

    String SERVICE_TYPE = "_siia._tcp.";

    void startServiceBroadcast(@NonNull String serviceName, int post, @NonNull String hostAddress);
    void stopServiceBroadcast();

    boolean isBroadcasting();

    PublishSubject<PostmanDiscoveryEvent> getDiscoveryEventStream();

    void discoverService(@NonNull String serviceName);

    void stopDiscovery();
}
