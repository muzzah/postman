package com.siia.postman.server;

import com.osiyent.sia.commons.core.log.Logcat;

import java.net.InetSocketAddress;

import javax.inject.Inject;

import io.reactivex.Flowable;

import static com.osiyent.sia.commons.core.check.Check.checkState;
import static io.reactivex.schedulers.Schedulers.newThread;


public class IPPostmanServer implements PostmanServer {
    private static final String TAG = Logcat.getTag();

    private final InetSocketAddress bindAddress;
    private Flowable<NetworkEvent> serverEventLoop;
    private Flowable<NetworkEvent> serverEventLoopDisposable;
    private MainNIOEventLoop mainNioEventLoop;


    @Inject
    IPPostmanServer() {
        this.bindAddress = new InetSocketAddress("0.0.0.0", 0);
    }


    @Override
    public Flowable<NetworkEvent> startServer() {
        checkState(!isRunning(), "Server is already running");
        mainNioEventLoop = new MainNIOEventLoop(bindAddress);
        serverEventLoop = Flowable.fromPublisher(mainNioEventLoop)
                .subscribeOn(newThread())
                .observeOn(newThread());

        return serverEventLoop;



    }

    @Override
    public void stopServer() {
        checkState(isRunning(), "Server is not running");
        if (mainNioEventLoop != null && mainNioEventLoop.isRunning()) {
            mainNioEventLoop.shutdownLoop();
        }
        serverEventLoop = null;
    }

    @Override
    public boolean isRunning() {
        return mainNioEventLoop != null
                && mainNioEventLoop.isRunning()
                && serverEventLoop != null;

    }

    @Override
    public void getClient(int clientId) {

    }



}
