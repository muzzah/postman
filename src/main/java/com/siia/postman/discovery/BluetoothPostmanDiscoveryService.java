package com.siia.postman.discovery;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;

import com.siia.commons.core.log.Logcat;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;

import io.reactivex.Scheduler;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.subjects.PublishSubject;

/**
 * Copyright Siia 2018
 */
@SuppressLint("MissingPermission")
public class BluetoothPostmanDiscoveryService implements PostmanDiscoveryService {

    private static final String TAG = Logcat.getTag();

    //TODO move this into properties
    // xxxxxxxx-0000-1000-8000-00805F9B34FB
    static final ParcelUuid SERVICE_ID = ParcelUuid.fromString("000001FA-0000-1000-8000-00805F9B34FB");
    private static final Pattern AP_DETAILS_PATTERN = Pattern.compile("^.{4}(DIRECT.+)\\|(.+)$");

    private static String AP_DEVICE_NAME_FORMAT = "SIIA:%s:%s:%d";

    private final PublishProcessor<PostmanDiscoveryEvent> eventStream;
    private final PublishSubject<PostmanDiscoveryEvent> oldEventStream;
    private final BluetoothBroadcaster bluetoothBroadcaster;
    private final BluetoothDiscoverer bluetoothDiscoverer;
    private AtomicBoolean isBroadcasting;
    private AtomicBoolean isDiscovering;
    private Scheduler computation;
    private Context ctx;


    @Inject
    public BluetoothPostmanDiscoveryService(BluetoothBroadcaster bluetoothBroadcaster,
                                            BluetoothDiscoverer bluetoothDiscoverer,
                                            @Named("computation") Scheduler computation, Context ctx) {
        this.bluetoothBroadcaster = bluetoothBroadcaster;
        this.bluetoothDiscoverer = bluetoothDiscoverer;
        this.computation = computation;
        this.ctx = ctx;
        this.eventStream = PublishProcessor.create();
        this.oldEventStream = PublishSubject.create();
        isBroadcasting = new AtomicBoolean(false);
        isDiscovering = new AtomicBoolean(false);
    }

    //TODO what if broadcasting fails?
    @Override
    public void startServiceBroadcast(@NonNull String serviceName, int port, @NonNull String hostAddress) {
        if (isBroadcasting.compareAndSet(false, true)) {
            Logcat.i(TAG, "Starting service broadcast");
            Logcat.v(TAG, "advertisingData=%s", serviceName);
            bluetoothBroadcaster.beginBroadcast(serviceName);
        } else {
            Logcat.w(TAG, "Already broadcasting");
        }

    }

    @Override
    public void stopServiceBroadcast() {
        if (isBroadcasting.compareAndSet(true, false)) {
            bluetoothBroadcaster.stopBroadcast();
        }
    }

    @Override
    public boolean isBroadcasting() {
        return isBroadcasting.get();
    }

    @Override
    public PublishSubject<PostmanDiscoveryEvent> getDiscoveryEventStream() {
        return oldEventStream;
    }

    @Override
    public void discoverService(@NonNull String serviceName) {
        if (isDiscovering.compareAndSet(false, true)) {
            Logcat.d(TAG, "Beginning search for %s", serviceName);
            bluetoothDiscoverer.findService(serviceName)
                    .observeOn(computation)
                    .subscribe(remoteAPDetails -> {
                                Logcat.d(TAG, "Found remote AP");
                                String apDetails = remoteAPDetails.getApName();
                                Logcat.v(TAG, apDetails);
                                Matcher matcher = AP_DETAILS_PATTERN.matcher(apDetails);

                                if (matcher.matches() && matcher.groupCount() == 2) {
                                    String apName = matcher.group(1);
                                    String sharedKey = matcher.group(2);
                                    oldEventStream.onNext(PostmanDiscoveryEvent.foundAP(apName, sharedKey));
                                    stopDiscovery();
                                } else {
                                    Logcat.w(TAG, "Discoverer found details but does not match");
                                    bluetoothDiscoverer.continueDiscovery(serviceName);
                                }
                            },
                            error -> Logcat.e(TAG, "Problem searching for service", error));
        } else {
            Logcat.w(TAG, "Already discovering");
        }

    }

    @Override
    public void stopDiscovery() {
        if (isDiscovering.compareAndSet(true, false)) {
            bluetoothDiscoverer.stopDiscovery();
        }
    }

}


