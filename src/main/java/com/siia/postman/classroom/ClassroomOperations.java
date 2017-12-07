package com.siia.postman.classroom;

import android.util.Log;

import com.siia.commons.core.log.Logcat;
import com.siia.postman.discovery.PostmanDiscoveryEvent;
import com.siia.postman.discovery.PostmanDiscoveryService;
import com.siia.postman.server.PostmanClient;
import com.siia.postman.server.PostmanServer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Provider;

import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;

import static com.siia.commons.core.check.Check.checkState;

public class ClassroomOperations {

    private static final String TAG = Logcat.getTag();
    private static final long RECONNECT_DELAY_SECONDS = 5;
    private final PostmanServer postmanServer;
    private final PostmanDiscoveryService discoveryService;
    private Provider<PostmanClient> clientProvider;
    private final Scheduler computationScheduler;
    private final AtomicBoolean isDiscoveryActive;
    private PostmanClient postmanClient;
    private Disposable clientDisposable;
    private Disposable discoveryDisposable;


    public ClassroomOperations(PostmanServer postmanServer, PostmanDiscoveryService discoveryService,
                               Provider<PostmanClient> clientProvider,
                               Scheduler computationScheduler) {
        this.postmanServer = postmanServer;
        this.discoveryService = discoveryService;
        this.clientProvider = clientProvider;
        this.computationScheduler = computationScheduler;
        this.isDiscoveryActive = new AtomicBoolean(false);
    }

    public void begin() {
        checkState(!hasClassStarted(), "Classroom already running");
        postmanServer.getServerEventsStream()
                .observeOn(computationScheduler)
                .filter(serverEvent -> !serverEvent.isNewMessage())
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

        if (postmanClient != null && postmanClient.isConnected()) {
            if (clientDisposable != null) {
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
        return postmanServer.isRunning() || (postmanClient != null && postmanClient.isConnected());
    }

    public boolean hasConnectionAlreadyStarted() {
        return isDiscoveryActive.get();
    }

    public void connectToClassroom() {
        checkState(!isDiscoveryActive.get() && !hasClassStarted(), "Discovery already started");
        if (isDiscoveryActive.compareAndSet(false, true)) {
            discoveryDisposable = discoveryService.getDiscoveryEventStream()
                    .observeOn(computationScheduler)
                    .subscribe(
                            discoveryEvent -> {

                                switch (discoveryEvent.type()) {
                                    case FOUND:
                                        if (postmanClient != null && postmanClient.isConnected()) {
                                            Log.w(TAG, "Service found but still connected");
                                            return;
                                        }

                                        connectToServer(discoveryEvent);

                                        break;
                                    case LOST:
                                        if (postmanClient != null && postmanClient.isConnected()) {
                                            Log.w(TAG, "Service lost but still connected");
                                            return;
                                        }
                                        break;
                                    case STARTED:
                                        isDiscoveryActive.set(true);
                                        break;
                                }

                            },
                            error -> {
                                isDiscoveryActive.set(false);
                                reconnect();
                            },
                            () -> {
                                isDiscoveryActive.set(false);
                            });

            discoveryService.discoverService();
        }
    }

    private void connectToServer(PostmanDiscoveryEvent discoveryEvent) {
        postmanClient = clientProvider.get();
        clientDisposable = postmanClient.getClientEventStream()
                .observeOn(computationScheduler)
                .doOnSubscribe(disposable -> postmanClient.connect(discoveryEvent.getHotname(), discoveryEvent.getPort()))
                .subscribe(
                        postmanClientEvent -> {
                            switch (postmanClientEvent.type()) {
                                case CONNECTED:
                                case DISCONNECTED:
                                default:
                            }
                        },
                        //Connection closed
                        error -> reconnect());

    }

    private void reconnect() {
        isDiscoveryActive.set(false);
        discoveryDisposable.dispose();
        discoveryService.stopDiscovery();
        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(RECONNECT_DELAY_SECONDS));
        } catch (InterruptedException e) {
            Log.e(TAG, "Thread waiting ");
        } finally {
            connectToClassroom();
        }
    }


}

