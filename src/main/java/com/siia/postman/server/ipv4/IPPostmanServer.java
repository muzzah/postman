package com.siia.postman.server.ipv4;

import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.ServerEvent;
import com.siia.postman.server.PostmanMessage;
import com.siia.postman.server.PostmanServer;

import java.net.InetSocketAddress;

import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static com.siia.commons.core.check.Check.checkState;


public class IPPostmanServer implements PostmanServer {
    private static final String TAG = Logcat.getTag();

    private final InetSocketAddress bindAddress;
    private IOEventLoop IOEventLoop;
    private PublishSubject<ServerEvent> classEventsStream;
    private Disposable clientJoinDisposable;

    public IPPostmanServer() {
        this.bindAddress = new InetSocketAddress("0.0.0.0", 8888);
        this.classEventsStream = PublishSubject.create();
    }


    @Override
    public PublishSubject<ServerEvent> getClassEventsStream() {
        return classEventsStream;
    }

    @Override
    public void serverStart() {
        checkState(!isRunning(), "Server is already running");

        IOEventLoop = new IOEventLoop(bindAddress);



        clientJoinDisposable = IOEventLoop.getServerEventsStream()
                .observeOn(Schedulers.computation())
                .subscribe(
                        event -> {
                            switch (event.type()) {
                                case CLIENT_JOIN:
                                    IOEventLoop.addMessageToQueue(new PostmanMessage("HELLO WORLD"), event.client());
                                    break;
                                case CLIENT_DISCONNECT:
                                    break;
                                case SERVER_LISTENING:
                                    classEventsStream.onNext(event);
                                    break;
                                default:
                                    Logcat.d(TAG, "Not processing event %s", event.type().name());
                                    break;
                            }


                        },
                        error -> {
                            Logcat.e(TAG, "Error on join stream", error);
                            clientJoinDisposable.dispose();
                        },
                        () -> {
                            Logcat.i(TAG, "Join Stream has ended");
                            clientJoinDisposable.dispose();
                        });

        IOEventLoop.startLooping();


    }

    @Override
    public void stopServer() {
        checkState(isRunning(), "Server is not running");
        if (IOEventLoop != null) {
            IOEventLoop.shutdownLoop();
        }
    }

    @Override
    public boolean isRunning() {
        return IOEventLoop != null
                && IOEventLoop.isRunning();

    }




}
