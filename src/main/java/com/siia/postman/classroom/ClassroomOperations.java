package com.siia.postman.classroom;

import android.util.Log;

import com.siia.commons.core.log.Logcat;
import com.siia.postman.discovery.PostmanDiscoveryEvent;
import com.siia.postman.discovery.PostmanDiscoveryService;
import com.siia.postman.server.PostmanClient;
import com.siia.postman.server.PostmanMessage;
import com.siia.postman.server.PostmanServer;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Provider;

import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static com.siia.commons.core.check.Check.checkState;

public class ClassroomOperations {
    public enum Answer {
        A, B, C, D;

        public boolean isEqualOrAfter(Answer answer) {
            return ordinal() >= answer.ordinal();
        }

        public int numberOfAnswers() {
            return ordinal() + 1;
        }
    }

    public static final int MAX_ANSWERS = Answer.values().length;
    public static final int MIN_ANSWERS = 2;


    private static final String TAG = Logcat.getTag();
    private static final long RECONNECT_DELAY_MILLISECONDS = 5000;
    private final PostmanServer postmanServer;
    private final PostmanDiscoveryService discoveryService;
    private Provider<PostmanClient> clientProvider;
    private final Scheduler computationScheduler;
    private final AtomicBoolean isDiscoveryActive;
    private final AtomicBoolean isRestarting;
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
        this.isRestarting = new AtomicBoolean(false);
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
                                    break;
                                case DISCONNECTED:
                                    reconnect();
                                    break;
                                case NEW_MESSAGE:
                                    postmanClient.sendMessage(new PostmanMessage(Task.TaskResponse.newBuilder().setTaskType(Task.TaskType.QUIZ)
                                            .setQuizUpdate(Task.QuizUpdate.newBuilder().setResponse(3)).build()));
                                    break;
                                default:
                            }
                        },
                        //Connection closed
                        error -> reconnect());

    }

    private void reconnect() {
        if(isRestarting.compareAndSet(false, true)) {
            Logcat.d(TAG, "Reconnecting");
            isDiscoveryActive.set(false);
            discoveryDisposable.dispose();
            discoveryService.stopDiscovery();
            if (clientDisposable != null) {
                clientDisposable.dispose();
            }

            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    connectToClassroom();
                    isRestarting.set(false);
                }
            };

            Timer timer = new Timer();
            timer.schedule(task, RECONNECT_DELAY_MILLISECONDS);
        }
    }


    public Observable<ClassUnderstandingEvent> gainUnderstanding(String name, int numberOfAnswers) {
        return postmanServer.getServerEventsStream().observeOn(Schedulers.computation())
                .doOnSubscribe(disposable -> {
                    Task.TaskCommand cmd = Task.TaskCommand.newBuilder().setTaskType(Task.TaskType.QUIZ)
                            .setQuizCmd(Task.QuizCommand.newBuilder().setQuestionCount(numberOfAnswers))
                            .build();

                    PostmanMessage msg = new PostmanMessage(cmd);
                    postmanServer.broadcastMessage(msg);
                })
                .filter(serverEvent -> serverEvent.isNewMessage() && serverEvent.message().isOfType(Task.TaskResponse.class))
                .map(serverEvent -> {
                    Task.TaskResponse update = serverEvent.message().getProtoObj();
                    return new ClassUnderstandingEvent(numberOfAnswers);
                });

    }
}

