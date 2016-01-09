package com.siia.postman.server;

import android.util.Log;

import com.osiyent.sia.commons.core.log.Logcat;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

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
    public void startServer() {
        if(serverEventLoop != null) {
            Logcat.w(TAG, "Server already started");
           return;
        }
        mainNioEventLoop = new MainNIOEventLoop(bindAddress);
        serverEventLoop = Observable.create(mainNioEventLoop)
                .observeOn(newThread())
                .subscribeOn(newThread()).
                        subscribe(new Subscriber<ByteBuffer>() {
                            @Override
                            public void onCompleted() {
                                Log.i(TAG, "Server Stopped");
                            }

                            @Override
                            public void onError(Throwable e) {
                                Log.e(TAG, "Abnormal Server Shutdown", e);
                            }

                            @Override
                            public void onNext(ByteBuffer byteBuffer) {

                            }
                        });

    }

    @Override
    public void stopServer() {
        mainNioEventLoop.shutdownLoop();
        serverEventLoop = null;
    }


}
