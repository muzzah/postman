package com.siia.postman.server;

import android.util.Log;

import com.osiyent.sia.commons.core.log.Logcat;

import java.net.InetSocketAddress;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;

import static rx.schedulers.Schedulers.newThread;

public class IpBasedServer implements Server {
    private static final String TAG = Logcat.getTag();
    private final InetSocketAddress bindAddress;
    private Subscription serverEventLoop;
    private MainNIOEventLoop mainNioEventLoop;

    public IpBasedServer() {
        bindAddress = new InetSocketAddress("0.0.0.0", 54345);
    }


    @Override
    public void startServer(final NetworkEventListener networkEventListener) {
        if (serverEventLoop != null) {
            Logcat.w(TAG, "Server already started");
            return;
        }
        mainNioEventLoop = new MainNIOEventLoop(bindAddress);
        serverEventLoop = Observable.create(mainNioEventLoop)
                .observeOn(newThread())
                .subscribeOn(newThread()).
                        subscribe(new Subscriber<NetworkEvent>() {
                            @Override
                            public void onCompleted() {
                                Log.i(TAG, "Server Stopped");
                            }

                            @Override
                            public void onError(Throwable e) {
                                Log.e(TAG, "Abnormal Server Shutdown", e);
                            }

                            @Override
                            public void onNext(NetworkEvent networkEvent) {
                                switch (networkEvent.type()) {
                                    case CLIENT_JOIN:
                                        networkEventListener.onClientJoin(networkEvent.clientId());
                                        break;
                                    case NEW_DATA:
                                        networkEventListener.onClientData(networkEvent.data(), networkEvent.clientId());
                                        break;
                                    case CLIENT_DISCONNECT:
                                        networkEventListener.onClientDisconnect(networkEvent.clientId());
                                        break;
                                    case SERVER_LISTENING:
                                        networkEventListener.onServerListening();
                                        break;
                                    default:
                                        Logcat.w(TAG, "Unrecognised Network Event : %s", networkEvent.type());
                                }
                            }
                        });

    }

    @Override
    public void stopServer() {
        if(mainNioEventLoop != null && mainNioEventLoop.isRunning()) {
            mainNioEventLoop.shutdownLoop();
        }
        if(serverEventLoop != null && !serverEventLoop.isUnsubscribed()) {
            serverEventLoop.unsubscribe();
        }
        serverEventLoop = null;
    }

    @Override
    public boolean isRunning() {
        return mainNioEventLoop != null && mainNioEventLoop.isRunning() && serverEventLoop != null && !serverEventLoop.isUnsubscribed();
    }

    @Override
    public void getClient(int clientId) {

    }


}
