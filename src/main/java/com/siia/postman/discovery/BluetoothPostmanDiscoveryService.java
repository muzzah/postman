package com.siia.postman.discovery;

import android.annotation.SuppressLint;
import android.support.annotation.NonNull;

import com.siia.commons.core.log.Logcat;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;
import javax.inject.Named;

import io.reactivex.Flowable;
import io.reactivex.Scheduler;

/**
 * Copyright Siia 2018
 */
@SuppressLint("MissingPermission")
public class BluetoothPostmanDiscoveryService implements PostmanDiscoveryService {

    private static final String TAG = Logcat.getTag();

    private final BluetoothBroadcaster bluetoothBroadcaster;
    private final BluetoothDiscoverer bluetoothDiscoverer;
    private final AtomicBoolean isBroadcasting;
    private final AtomicBoolean isDiscovering;
    private final Scheduler io;


    @Inject
    public BluetoothPostmanDiscoveryService(BluetoothBroadcaster bluetoothBroadcaster,
                                            BluetoothDiscoverer bluetoothDiscoverer,
                                            @Named("io") Scheduler io) {
        this.bluetoothBroadcaster = bluetoothBroadcaster;
        this.bluetoothDiscoverer = bluetoothDiscoverer;
        this.io = io;
        this.isBroadcasting = new AtomicBoolean(false);
        this.isDiscovering = new AtomicBoolean(false);
    }

    @Override
    public Flowable<PostmanBroadcastEvent> startServiceBroadcast(@NonNull String serviceName, int port, @NonNull InetAddress hostAddress) {

        return Flowable.<PostmanBroadcastEvent>fromPublisher(subsciber -> {
            if(isBroadcasting.compareAndSet(false, true)) {
                Logcat.i(TAG, "Starting service broadcast");
                Logcat.v(TAG, "advertisingData=%s", serviceName);
                try {
                    /**
                     * We are missing an oncomplete call as the begin broadcast locks
                     * and waits till either an error or a stop call from the client.
                     * Should not be a problem though
                     */
                    bluetoothBroadcaster.beginBroadcast(serviceName, subsciber);
                } catch(Throwable e) {
                    Logcat.e(TAG, "Problem while beginning broadcast", e);
                    subsciber.onError(e);
                } finally {
                    stopServiceBroadcast();
                }
            } else {
                Logcat.w(TAG, "Already broadcasting");
                subsciber.onNext(PostmanBroadcastEvent.alreadyBroadcasting());
                subsciber.onComplete();
            }
        }).subscribeOn(io);
    }

    @Override
    public void stopServiceBroadcast() {
        if (isBroadcasting.compareAndSet(true, false)) {
            Logcat.v(TAG, "Stopping service broadcast");
            bluetoothBroadcaster.stopBroadcast();
        }
    }


    @Override
    public Flowable<PostmanDiscoveryEvent> discoverService(@NonNull String serviceName) {

        return Flowable.<PostmanDiscoveryEvent>fromPublisher(subscriber -> {
            if (isDiscovering.compareAndSet(false, true)) {
                try {
                    bluetoothDiscoverer.findService(serviceName, subscriber);
                } catch(Throwable e) {
                    subscriber.onError(e);
                } finally {
                    isDiscovering.set(false);
                }

            } else {
                subscriber.onNext(PostmanDiscoveryEvent.alreadyDiscovering());
                subscriber.onComplete();
            }
        }).subscribeOn(io);

    }

    @Override
    public void stopDiscovery() {
        if (isDiscovering.compareAndSet(true, false)) {
            bluetoothDiscoverer.stopDiscovery();
        }
    }

}


