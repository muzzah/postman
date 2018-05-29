package org.postman.discovery;


import android.support.annotation.NonNull;

import java.net.InetAddress;


import io.reactivex.Flowable;

/**
 * Handle service registration and broadcasting to devices interested.
 *
 * For broadcasting & discovering, Any error that is delivered means that a new broadcasting session needs to be created
 * and started. For a successful start, the event stream will be active until the broadcast is explicitly stopped
 * or an error occurs, e.g system unregisters the broadcaster due to an error.
 *
 */
public interface PostmanDiscoveryService {

    Flowable<PostmanBroadcastEvent> startServiceBroadcast(@NonNull String serviceType, @NonNull String serviceName, int port, @NonNull InetAddress hostAddress);

    void stopServiceBroadcast();

    Flowable<PostmanDiscoveryEvent> discoverService(@NonNull String serviceType, @NonNull String serviceName, @NonNull InetAddress addressToSearchOn);

    void stopDiscovery();
}
