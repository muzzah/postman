package com.siia.postman;


import com.osiyent.sia.commons.core.log.Logcat;
import com.siia.postman.discovery.PostmanDiscoveryService;
import com.siia.postman.server.NetworkEvent;
import com.siia.postman.server.PostmanServer;

import javax.inject.Inject;

import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;


public class Postman {
    private static final String TAG = Logcat.getTag();

    private PostmanServer postmanServer;
    private PostmanDiscoveryService discoveryService;
    private Disposable disposable;


    @Inject
    public Postman(PostmanServer postmanServer, PostmanDiscoveryService discoveryService) {
        this.postmanServer = postmanServer;
        this.discoveryService = discoveryService;
    }


    public void discoverServer() {
        discoveryService.startServiceDiscovery();
    }

    public void cancelDiscovery() {
        discoveryService.stopServiceDiscovery();
    }

    public void startServer() {
        Flowable<NetworkEvent> postmanServerEvents = postmanServer.startServer();

        disposable = postmanServerEvents.subscribe(
                (event) -> {
                    switch (event.type()) {
                        case CLIENT_JOIN:
                            break;
                        case NEW_DATA:
                            break;
                        case CLIENT_DISCONNECT:
                            break;
                        case SERVER_LISTENING:
                            discoveryService.startServiceBroadcast(event.getListeningPort(), event.getHostAddress());
                            break;
                        default:
                            Logcat.w(TAG, "Unrecognised Network Event : %s", event.type());
                    }
                },
                (error) -> {
                    Logcat.e(TAG, "Abnormal PostmanServer Shutdown", error);
                    discoveryService.stopServiceBroadcast();
                },
                () -> {
                    Logcat.i(TAG, "PostmanServer Stopped");
                    discoveryService.stopServiceBroadcast();
                }
        );
    }

    public void stopServer() {
        disposable.dispose();
        postmanServer.stopServer();
    }
}
