package com.siia.postman.classroom;

import com.siia.commons.core.log.Logcat;
import com.siia.postman.discovery.PostmanDiscoveryEvent;
import com.siia.postman.discovery.PostmanDiscoveryService;
import com.siia.postman.server.PostmanClient;
import com.siia.postman.server.ServerEvent;
import com.siia.postman.server.PostmanServer;
import com.siia.postman.server.nio.NIOPostmanClient;

import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static com.siia.commons.core.check.Check.checkState;

public class ClassroomOperations {

    private static final String TAG = Logcat.getTag();
    private final PostmanServer postmanServer;
    private final PostmanDiscoveryService discoveryService;
    private final AtomicBoolean isDiscoveryActive;
    private PostmanClient postmanClient;
    private final CompositeDisposable disposables;


    public ClassroomOperations(PostmanServer postmanServer, PostmanDiscoveryService discoveryService) {
        this.postmanServer = postmanServer;
        this.discoveryService = discoveryService;
        this.isDiscoveryActive = new AtomicBoolean(false);
        this.disposables = new CompositeDisposable();


    }

    public void begin() {
        checkState(!hasClassStarted(), "Classroom already running");
        PublishSubject<ServerEvent> serverEventsStream = postmanServer.getServerEventsStream();
        serverEventsStream
                .observeOn(Schedulers.computation())
                .subscribe(
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
        if (postmanServer != null && postmanServer.isRunning()) {
            postmanServer.stopServer();
            //Service Broadcast should be stopped through event coming through
        }

        if (postmanClient != null && postmanClient.isConnected()) {
            disposables.dispose();
            postmanClient.disconnect();
        }

    }

    private void stopServiceBroadcast() {
        if (discoveryService.isBroadcasting()) {
            discoveryService.stopServiceBroadcast();
        }
    }

    public boolean hasClassStarted() {
        return (postmanServer != null && postmanServer.isRunning()) || (postmanClient != null && postmanClient.isConnected());
    }

    public boolean hasConnectionAlreadyStarted() {
        return isDiscoveryActive.get();
    }

    public void connect() {
        checkState(!isDiscoveryActive.get() && !hasClassStarted(), "Discovery already started");
        if (isDiscoveryActive.compareAndSet(false, true)) {
            discoveryService.getDiscoveryEventStream()
                    .observeOn(Schedulers.computation())
                    .subscribe(
                            discoveryEvent -> {

                                switch (discoveryEvent.type()) {
                                    case FOUND:
                                        connectToServerAsClient(discoveryEvent);
                                        isDiscoveryActive.set(false);
                                        break;
                                    case NOT_FOUND:
                                        isDiscoveryActive.set(false);
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

            discoveryService.discoverService();
        }
    }

    private void connectToServerAsClient(PostmanDiscoveryEvent discoveryEvent) {
        postmanClient = new NIOPostmanClient();
        disposables.add(postmanClient.getClientEventStream()
                .observeOn(Schedulers.computation())
                .subscribe(postmanClientEvent -> {
                            switch (postmanClientEvent.type()) {
                                case CONNECTED:

                                case DISCONNECTED:

                                default:
                            }
                        },
                        error -> {
                            connect();
                        },
                        () -> {
                            connect();
                        }));

        postmanClient.connect(discoveryEvent.getHotname(), discoveryEvent.getPort());
    }


}

