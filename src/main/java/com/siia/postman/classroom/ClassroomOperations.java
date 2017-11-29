package com.siia.postman.classroom;

import com.siia.commons.core.log.Logcat;
import com.siia.postman.discovery.PostmanDiscoveryService;
import com.siia.postman.server.PostmanClient;
import com.siia.postman.server.ServerEvent;
import com.siia.postman.server.PostmanServer;
import com.siia.postman.server.ipv4.IPPostmanClient;

import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static com.siia.commons.core.check.Check.checkState;

public class ClassroomOperations {

    private static final String TAG = Logcat.getTag();
    private final PostmanServer postmanServer;
    private final PostmanDiscoveryService discoveryService;
    private final AtomicBoolean isDiscoveryActive;


    public ClassroomOperations(PostmanServer postmanServer, PostmanDiscoveryService discoveryService) {
        this.postmanServer = postmanServer;
        this.discoveryService = discoveryService;
        this.isDiscoveryActive = new AtomicBoolean(false);
    }

    public void begin() {
        checkState(!hasClassStarted(), "Classroom already running");
        PublishSubject<ServerEvent> classroomEvents = postmanServer.getClassEventsStream();
        classroomEvents.observeOn(Schedulers.computation()).subscribe(
                event -> {
                    switch (event.type()) {
                        case SERVER_LISTENING:
                            discoveryService.startServiceBroadcast(event.getListeningPort(), event.getHostAddress());
                            break;
                        default:
                            Logcat.d(TAG, "Not processing event %s", event.type().name());
                            break;
                    }
                }, error -> {
                    Logcat.e(TAG, "Classroom has ended unexpectedly", error);
                    stopServiceBroadcast();
                }, () -> {
                    Logcat.i(TAG, "Classroom has ended");
                    stopServiceBroadcast();
                });

        postmanServer.serverStart();

    }

    public void end() {
        checkState(hasClassStarted(), "Classroom has not started");
        postmanServer.stopServer();
        stopServiceBroadcast();

    }

    private void stopServiceBroadcast() {
        if(discoveryService.isBroadcasting()) {
            discoveryService.stopServiceBroadcast();
        }
    }

    public boolean hasClassStarted() {
        return postmanServer.isRunning();
    }

    public boolean hasConnectionAlreadyStarted() {
        return isDiscoveryActive.get();
    }

    public void connect() {
        checkState(!isDiscoveryActive.get(), "Discovery already started");
        if (isDiscoveryActive.compareAndSet(false, true)) {
            discoveryService.discoverService()
                    .subscribe(
                            discoveryEvent -> {

                                switch (discoveryEvent.type()) {
                                    case FOUND:
                                        PostmanClient postmanClient = new IPPostmanClient();
                                        postmanClient.connect(discoveryEvent.getHotname(), discoveryEvent.getPort());
                                        break;
                                    case STARTED:
                                        isDiscoveryActive.set(true);
                                        break;
                                }

                            },
                            error -> {
                                isDiscoveryActive.set(false);
                            },
                            () -> {
                                isDiscoveryActive.set(false);
                            });
        }
    }


}

