package com.siia.postman.server;

import com.osiyent.sia.commons.core.log.Logcat;

import java.net.InetSocketAddress;

import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;

import static io.reactivex.schedulers.Schedulers.newThread;


public class IPServer implements Server {
    private static final String TAG = Logcat.getTag();
    private final InetSocketAddress bindAddress;
    private Flowable<NetworkEvent> serverEventLoop;
    private Disposable serverEventLoopDisposable;
    private MainNIOEventLoop mainNioEventLoop;

    public IPServer() {
        bindAddress = new InetSocketAddress("0.0.0.0", 54345);
    }


    @Override
    public void startServer(final NetworkEventListener networkEventListener) {
        if (serverEventLoop != null) {
            Logcat.w(TAG, "Server already started");
            return;
        }
        mainNioEventLoop = new MainNIOEventLoop(bindAddress);
        serverEventLoop = Flowable.fromPublisher(mainNioEventLoop)
                .subscribeOn(newThread())
                .observeOn(newThread());

        serverEventLoopDisposable = serverEventLoop.subscribe(

                (event) -> {
                    switch (event.type()) {
                        case CLIENT_JOIN:
                            networkEventListener.onClientJoin(event.clientId());
                            break;
                        case NEW_DATA:
                            networkEventListener.onClientData(event.data(), event.clientId());
                            break;
                        case CLIENT_DISCONNECT:
                            networkEventListener.onClientDisconnect(event.clientId());
                            break;
                        case SERVER_LISTENING:
                            networkEventListener.onServerListening();
                            break;
                        default:
                            Logcat.w(TAG, "Unrecognised Network Event : %s", event.type());
                    }
                },
                (error) -> Logcat.e(TAG, "Abnormal Server Shutdown", error),
                () -> Logcat.i(TAG, "Server Stopped")
        );

    }

    @Override
    public void stopServer() {
        if (mainNioEventLoop != null && mainNioEventLoop.isRunning()) {
            mainNioEventLoop.shutdownLoop();
        }
//        if (serverEventLoop != null && !serverEventLoopDisposable.isDisposed()) {
//            serverEventLoopDisposable.dispose();
//        }
        serverEventLoop = null;
    }

    @Override
    public boolean isRunning() {
        return mainNioEventLoop != null && mainNioEventLoop.isRunning() && serverEventLoop != null && !serverEventLoopDisposable.isDisposed();
    }

    @Override
    public void getClient(int clientId) {

    }


}
