package com.siia.postman.discovery;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;

import com.siia.commons.core.android.AndroidUtils;
import com.siia.commons.core.log.Logcat;

import org.reactivestreams.Subscriber;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import javax.inject.Inject;
import javax.inject.Named;

import io.reactivex.Flowable;
import io.reactivex.Scheduler;

import static com.siia.commons.core.concurrency.ConcurrencyUtils.awaitLatch;

/**
 * Copyright Siia 2018
 */

@SuppressLint("MissingPermission")
public class BluetoothDiscoverer {


    private static final String TAG = Logcat.getTag();
    private final BluetoothAdapter bluetoothAdapter;
    private final Context ctx;
    private final AndroidUtils androidUtils;
    private Scheduler io;
    private CountDownLatch bluetoothEnabledLatch;
    private CountDownLatch deviceFoundLatch;
    private BluetoothStateReceiver receiver;
    private ScanCallback leScanCallback;
    private Subscriber<? super RemoteAPDetails> subscriber;

    @Inject
    public BluetoothDiscoverer(BluetoothAdapter bluetoothAdapter, Context ctx, AndroidUtils androidUtils, @Named("io") Scheduler io) {
        this.bluetoothAdapter = bluetoothAdapter;
        this.ctx = ctx;
        this.androidUtils = androidUtils;
        this.io = io;
    }

    @AnyThread
    public Flowable<RemoteAPDetails> findService(@NonNull String nameToFind) {
        return Flowable.<RemoteAPDetails>fromPublisher(subscriber -> {

            if (!bluetoothAdapter.isEnabled()) {
                registerReceiver();

                bluetoothEnabledLatch = new CountDownLatch(1);
                boolean enable = bluetoothAdapter.enable();
                Logcat.result_v(TAG, "enable bluetooth", enable);

                if (!enable) {
                    subscriber.onError(new UnexpectedDiscoveryProblem("Could not enable bluetooth"));
                    return;
                }


                awaitLatch(bluetoothEnabledLatch, 10);
            }

            if (!bluetoothAdapter.isEnabled()) {
                subscriber.onError(new UnexpectedDiscoveryProblem("Bluetooth not enabled"));
                return;
            }
            this.subscriber = subscriber;
            deviceFoundLatch = new CountDownLatch(1);
            startDiscovery(nameToFind, subscriber);


            awaitLatch(deviceFoundLatch);
            subscriber.onComplete();
        }).subscribeOn(io);
    }

    public void stopDiscovery() {
        androidUtils.unregisterReceiverQuietly(receiver);
        bluetoothEnabledLatch.countDown();
        deviceFoundLatch.countDown();
        bluetoothAdapter.getBluetoothLeScanner().stopScan(leScanCallback);
        if (bluetoothAdapter.isEnabled()) {
            Logcat.result_v(TAG, "Disabling Adapter", bluetoothAdapter.disable());
        }
    }


    private void startDiscovery(String nameToFind, Subscriber<? super RemoteAPDetails> subscriber) {

        BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                //TODO Check this
                .setMatchMode(ScanSettings.MATCH_MODE_STICKY)
                .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                .setReportDelay(0)
                .build();

        leScanCallback = new LeScanCallback(nameToFind, subscriber);
        scanner.startScan(Collections.emptyList(), scanSettings, leScanCallback);
    }


    private void registerReceiver() {
        receiver = new BluetoothStateReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        ctx.registerReceiver(receiver, filter);
    }

    public void continueDiscovery(String serviceName) {
        startDiscovery(serviceName, subscriber);
    }

    private class LeScanCallback extends ScanCallback {
        private final CharSequence nameToFind;
        private final Subscriber<? super RemoteAPDetails> subscriber;

        private LeScanCallback(CharSequence nameToFind, Subscriber<? super RemoteAPDetails> subscriber) {
            this.nameToFind = nameToFind;
            this.subscriber = subscriber;
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Logcat.v(TAG, "Found device %s", result.toString());

            ScanRecord scanRecord = result.getScanRecord();
            if (scanRecord == null) {
                return;
            }

            BluetoothDevice device = result.getDevice();

            if (device != null && device.getName() != null && device.getName().contains(nameToFind)) {
                Logcat.d(TAG, "Found device : %s", device.getName());
                bluetoothAdapter.getBluetoothLeScanner().stopScan(this);
                subscriber.onNext(new RemoteAPDetails(device.getName()));

            }

        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            results.forEach(scanResult -> onScanResult(0, scanResult));
        }

        @Override
        public void onScanFailed(int errorCode) {
            Logcat.e(TAG, "Problem when scanning for devices %d", errorCode);
            subscriber.onError(new UnexpectedDiscoveryProblem("BT problem when scanning for devices"));

        }
    }

    private class BluetoothStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (bluetoothEnabled(intent)) {
                Logcat.d(TAG, "Bluetooth enabled");
                bluetoothEnabledLatch.countDown();
            }
        }

        private boolean bluetoothEnabled(Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, Integer.MIN_VALUE);
                return state == BluetoothAdapter.STATE_ON;
            }
            return false;
        }
    }

    class UnexpectedDiscoveryProblem extends RuntimeException {
        public UnexpectedDiscoveryProblem(String message) {
            super(message);
        }
    }


    static class RemoteAPDetails {
        private final String apName;

        RemoteAPDetails(String apName) {
            this.apName = apName;
        }

        public String getApName() {
            return apName;
        }
    }
}
