package com.siia.postman.discovery;

import android.support.annotation.NonNull;

import com.siia.commons.core.log.Logcat;

import java.net.InetAddress;

import javax.inject.Inject;
import javax.inject.Named;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;

/**
 * Copyright Siia 2018
 */
public class BluetoothPostmanDiscoveryService implements PostmanDiscoveryService {

    private static final String TAG = Logcat.getTag();

    private final BluetoothBroadcaster bluetoothBroadcaster;
    private final BluetoothDiscoverer bluetoothDiscoverer;
    private final Scheduler io;


    @Inject
    public BluetoothPostmanDiscoveryService(BluetoothBroadcaster bluetoothBroadcaster,
                                            BluetoothDiscoverer bluetoothDiscoverer,
                                            @Named("io") Scheduler io) {
        this.bluetoothBroadcaster = bluetoothBroadcaster;
        this.bluetoothDiscoverer = bluetoothDiscoverer;
        this.io = io;
    }

    @Override
    public Flowable<PostmanBroadcastEvent> startServiceBroadcast(@NonNull String serviceName, int port, @NonNull InetAddress hostAddress) {

        return Flowable.<PostmanBroadcastEvent>create(subscriber -> {
            Logcat.i(TAG, "Starting service broadcast");
            Logcat.v(TAG, "advertisingData=%s", serviceName);
            try {
                /**
                 * We are missing an oncomplete call as the begin broadcast locks
                 * and waits till either an error or a stop call from the client.
                 * Should not be a problem though
                 */
                bluetoothBroadcaster.beginBroadcast(serviceName, subscriber);
                if(!subscriber.isCancelled()) {
                    subscriber.onComplete();
                }
            } catch (Throwable e) {
                Logcat.e(TAG, "Problem while beginning broadcast", e);
                subscriber.tryOnError(e);
            } finally {
                stopServiceBroadcast();
            }
        }, BackpressureStrategy.BUFFER).subscribeOn(io);
    }

    @Override
    public void stopServiceBroadcast() {
        bluetoothBroadcaster.stopBroadcast();
    }


    @Override
    public Flowable<PostmanDiscoveryEvent> discoverService(@NonNull String serviceName, @NonNull InetAddress addressToSearchOn) {

        return Flowable.<PostmanDiscoveryEvent>create(subscriber -> {
            try {
                bluetoothDiscoverer.findService(serviceName, subscriber);

                if(!subscriber.isCancelled()) {
                    subscriber.onComplete();
                }

            } catch (Throwable e) {
                subscriber.tryOnError(e);
            } finally {
                stopDiscovery();
            }
        }, BackpressureStrategy.LATEST).subscribeOn(io);

    }

    @Override
    public void stopDiscovery() {
        bluetoothDiscoverer.stopDiscovery();
    }

}


