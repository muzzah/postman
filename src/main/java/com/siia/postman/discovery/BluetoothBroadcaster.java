package com.siia.postman.discovery;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import com.siia.commons.core.android.AndroidUtils;
import com.siia.commons.core.log.Logcat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import static com.siia.commons.core.concurrency.ConcurrencyUtils.awaitLatch;
import static com.siia.commons.core.concurrency.ConcurrencyUtils.sleepQuietly;
import static com.siia.commons.core.concurrency.ConcurrencyUtils.tryAction;

/**
 * Copyright Siia 2018
 */
@SuppressLint("MissingPermission")
public class BluetoothBroadcaster extends AdvertiseCallback {

    private static final String TAG = Logcat.getTag();
    private final BluetoothAdapter bluetoothAdapter;
    private final Context ctx;
    private final AndroidUtils androidUtils;
    private CountDownLatch deviceNameLatch;
    private CountDownLatch btEnabledLatch;
    private BluetoothStateReceiver receiver;
    private String deviceNameToBroadcast;

    @Inject
    public BluetoothBroadcaster(BluetoothAdapter bluetoothAdapter, Context ctx, AndroidUtils androidUtils) {
        this.bluetoothAdapter = bluetoothAdapter;
        this.ctx = ctx;
        this.androidUtils = androidUtils;
        this.receiver = new BluetoothStateReceiver();
    }


    @WorkerThread
    public void beginBroadcast(@NonNull String deviceName) {
        deviceNameToBroadcast = deviceName;
        //TODO handle error scenarios

        if (!bluetoothAdapter.isEnabled()) {
            Logcat.d(TAG, "Bluetooth disabled, enabling");
            registerReceiver();
            btEnabledLatch = new CountDownLatch(1);
            bluetoothAdapter.enable();
            awaitLatch(btEnabledLatch, 10);
            if(bluetoothAdapter.isEnabled()) {
                Logcat.d(TAG, "BT Enabled");
            } else {
                Logcat.e(TAG, "BT could not be enabled");
                return;
            }
        }

        sleepQuietly(TimeUnit.SECONDS, 3);

        registerLEService();
    }


    @AnyThread
    public void stopBroadcast() {
        androidUtils.unregisterReceiverQuietly(receiver);
        bluetoothAdapter.getBluetoothLeAdvertiser().stopAdvertising(this);

        if (bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.disable();
        }
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
        ctx.registerReceiver(receiver, filter);
    }

    private void registerLEService() {
        deviceNameLatch = new CountDownLatch(1);
        if (!tryAction(() -> bluetoothAdapter.setName(deviceNameToBroadcast), TimeUnit.MILLISECONDS, 500, 3)) {
            Logcat.e(TAG, "Device name cannot be set");
            return;
        }


        awaitLatch(deviceNameLatch, 2);

        if(!bluetoothAdapter.getName().equals(deviceNameToBroadcast)) {
            Logcat.e(TAG, "BT Device name change Failed");
            return;
        }


        BluetoothLeAdvertiser advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(false)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setTimeout(0)
                .build();

        AdvertiseData advertiseData = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build();

        //TODO handle error scenario
        advertiser.startAdvertising(advertiseSettings, advertiseData, this);

    }

    @Override
    public void onStartSuccess(AdvertiseSettings settingsInEffect) {
        Logcat.d(TAG, "Registered advertising data successfully");
        Logcat.v(TAG, settingsInEffect.toString());
        Logcat.v(TAG, "DeviceName=%s", bluetoothAdapter.getName());
    }

    @Override
    public void onStartFailure(int errorCode) {
        Logcat.e(TAG, "Could not register advertising data %d", errorCode);
    }

    private class BluetoothStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (bluetoothEnabled(intent)) {
                Logcat.d(TAG, "Bluetooth enabled");
                btEnabledLatch.countDown();
            }

            if(nameChanged(intent)) {
                Logcat.d(TAG, "Device name changed %s", bluetoothAdapter.getName());
                deviceNameLatch.countDown();
            }
        }

        private boolean nameChanged(Intent intent) {
            if (BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED.equals(intent.getAction())) {
                String name = intent.getStringExtra(BluetoothAdapter.EXTRA_LOCAL_NAME);
                Logcat.v(TAG, "Device name extra %s", name);
                return deviceNameToBroadcast.equals(name);
            }
            return false;
        }

        private boolean bluetoothEnabled(Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, Integer.MIN_VALUE);
                return state == BluetoothAdapter.STATE_ON && bluetoothAdapter.isEnabled();
            }
            return false;
        }
    }
}
