package com.siia.postman.classroom;

import com.siia.commons.core.log.Logcat;
import com.siia.postman.discovery.PostmanDiscoveryEvent;
import com.siia.postman.discovery.PostmanDiscoveryService;
import com.siia.postman.server.PostmanClient;
import com.siia.postman.server.PostmanServer;
import com.siia.postman.server.ServerEvent;

import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.PublishSubject;

import static com.siia.commons.core.check.Check.checkState;

public class ClassroomOperations {

    private static final String TAG = Logcat.getTag();
    private final PostmanServer postmanServer;
    private final PostmanDiscoveryService discoveryService;
    private final Scheduler computationScheduler;
    private final AtomicBoolean isDiscoveryActive;
    private final PostmanClient postmanClient;
    private Disposable clientDisposable;


    public ClassroomOperations(PostmanServer postmanServer, PostmanDiscoveryService discoveryService,
                               PostmanClient postmanClient, Scheduler computationScheduler) {
        this.postmanServer = postmanServer;
        this.discoveryService = discoveryService;
        this.computationScheduler = computationScheduler;
        this.isDiscoveryActive = new AtomicBoolean(false);
        this.postmanClient = postmanClient;
    }

    public void begin() {
        checkState(!hasClassStarted(), "Classroom already running");
        PublishSubject<ServerEvent> serverEventsStream = postmanServer.getServerEventsStream();
        serverEventsStream
                .observeOn(computationScheduler)
                .subscribe(
                        event -> {
                            switch (event.type()) {
                                case SERVER_LISTENING:
                                    discoveryService.startServiceBroadcast(event.getListeningPort(), event.getHostAddress());
                                    break;
                                case CLIENT_JOIN:
                                    break;
                                case CLIENT_DISCONNECT:
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
        if (postmanServer.isRunning()) {
            postmanServer.stopServer();
            //Service Broadcast should be stopped through event coming through
        }

        if (postmanClient.isConnected()) {
            if(clientDisposable != null) {
                clientDisposable.dispose();
            }
            postmanClient.disconnect();
            //If we are discovering?
        }

    }

    private void stopServiceBroadcast() {
        if (discoveryService.isBroadcasting()) {
            discoveryService.stopServiceBroadcast();
        }
    }

    public boolean hasClassStarted() {
        return postmanServer.isRunning() || postmanClient.isConnected();
    }

    public boolean hasConnectionAlreadyStarted() {
        return isDiscoveryActive.get();
    }

    public void connect() {
        checkState(!isDiscoveryActive.get() && !hasClassStarted(), "Discovery already started");
        if (isDiscoveryActive.compareAndSet(false, true)) {
            discoveryService.getDiscoveryEventStream()
                    .observeOn(computationScheduler)
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
        clientDisposable = postmanClient.getClientEventStream()
                .observeOn(computationScheduler)
                .subscribe(postmanClientEvent -> {
                            switch (postmanClientEvent.type()) {
                                case CONNECTED:
                                case DISCONNECTED:
                                default:
                            }
                        },
                        error -> {
                        },
                        () -> {
                        });

        postmanClient.connect(discoveryEvent.getHotname(), discoveryEvent.getPort());
    }


}

