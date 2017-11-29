package com.siia.postman.server.nio;

import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.ServerEvent;
import com.siia.postman.server.PostmanMessage;
import com.siia.postman.server.PostmanServer;

import java.net.InetSocketAddress;

import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static com.siia.commons.core.check.Check.checkState;


public class NIOPostmanServer implements PostmanServer {
    private static final String TAG = Logcat.getTag();

    private final InetSocketAddress bindAddress;
    private ServerEventLoop serverEventLoop;
    private PublishSubject<ServerEvent> serverEventsStream;
    private Disposable clientJoinDisposable;

    public NIOPostmanServer() {
        this.bindAddress = new InetSocketAddress("0.0.0.0", 8888);
        this.serverEventsStream = PublishSubject.create();
    }


    @Override
    public PublishSubject<ServerEvent> getServerEventsStream() {
        return serverEventsStream;
    }

    @Override
    public void serverStart() {
        checkState(!isRunning(), "Server is already running");

        serverEventLoop = new ServerEventLoop(bindAddress);



        clientJoinDisposable = serverEventLoop.getServerEventsStream()
                .observeOn(Schedulers.computation())
                .subscribe(
                        event -> {
                            switch (event.type()) {
                                case CLIENT_JOIN:
                                    serverEventLoop.addMessageToQueue(new PostmanMessage("HELLO WORLD"), event.client());
                                    Logcat.i(TAG, "Client connected [%s]", event.client().getClientId());
                                    break;
                                case CLIENT_DISCONNECT:
                                    Logcat.i(TAG, "Client disconnected [%s]", event.client().getClientId());
                                    break;
                                case SERVER_LISTENING:
                                    serverEventsStream.onNext(event);
                                    break;
                                default:
                                    Logcat.d(TAG, "Not processing event %s", event.type().name());
                                    break;
                            }


                        },
                        error -> {
                            Logcat.e(TAG, "Error on server events stream", error);
                            clientJoinDisposable.dispose();
                            serverEventsStream.onError(error);
                        },
                        () -> {
                            Logcat.i(TAG, "Server Events stream has ended");
                            clientJoinDisposable.dispose();
                            serverEventsStream.onComplete();
                        });

        serverEventLoop.startLooping();


    }

    @Override
    public void stopServer() {
        checkState(isRunning(), "Server is not running");
        if (serverEventLoop != null) {
            serverEventLoop.shutdownLoop();
        }
    }

    @Override
    public boolean isRunning() {
        return serverEventLoop != null
                && serverEventLoop.isRunning();

    }




}
