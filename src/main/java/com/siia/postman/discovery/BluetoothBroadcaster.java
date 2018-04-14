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
import com.siia.commons.core.concurrency.ConcurrencyUtils;
import com.siia.commons.core.constants.TimeConstant;
import com.siia.commons.core.log.Logcat;
import com.siia.commons.core.timing.StopWatch;

import org.reactivestreams.Subscriber;

import java.util.concurrent.CountDownLatch;

import javax.inject.Inject;

import static com.siia.commons.core.concurrency.ConcurrencyUtils.awaitLatch;
import static com.siia.commons.core.concurrency.ConcurrencyUtils.sleepQuietly;
import static com.siia.commons.core.concurrency.ConcurrencyUtils.tryAction;

/**
 * Settings.Global.BLUETOOTH_DISCOVERABILITY exists to enable disable discoverability without user
 * intervention
 * <p>
 * Copyright Siia 2018
 */
@SuppressLint("MissingPermission")
public class BluetoothBroadcaster {

    private static final String TAG = Logcat.getTag();
    private static final int RETRY_COUNT = 3;
    private final BluetoothAdapter bluetoothAdapter;
    private final Context ctx;
    private final AndroidUtils androidUtils;
    private final StopWatch stopWatch;
    private BTAdvertisingCallback advertisingCallback;
    private CountDownLatch deviceNameLatch;
    private CountDownLatch btEnabledLatch;
    private CountDownLatch btDisableLatch;
    private CountDownLatch stopAdvertisingLatch;
    private BluetoothStateReceiver receiver;
    private String deviceNameToBroadcast;

    @Inject
    BluetoothBroadcaster(BluetoothAdapter bluetoothAdapter, Context ctx, AndroidUtils androidUtils, StopWatch stopWatch) {
        this.bluetoothAdapter = bluetoothAdapter;
        this.ctx = ctx;
        this.androidUtils = androidUtils;
        this.stopWatch = stopWatch;
        this.receiver = new BluetoothStateReceiver();
    }


    @WorkerThread
    public void beginBroadcast(@NonNull String deviceName, @NonNull Subscriber<? super PostmanBroadcastEvent> subscriber) {
        deviceNameToBroadcast = deviceName;
        //TODO handle error scenarios

        registerReceiver();
        if (bluetoothAdapter.isEnabled()) {
            btDisableLatch = new CountDownLatch(1);

            stopWatch.start();
            boolean result = bluetoothAdapter.disable();


            if (!result) {
                stopWatch.stopAndPrintMillis("Disable BT Failed");
                Logcat.w(TAG, "Could not disable BT, continuing anyway");
            } else {
                awaitLatch(btDisableLatch, TimeConstant.NETWORK_LATCH_TIME_WAIT);
                stopWatch.stopAndPrintMillis("Disable BT");
            }
        }

        if (!bluetoothAdapter.isEnabled()) {
            Logcat.d(TAG, "Bluetooth disabled, enabling");
            btEnabledLatch = new CountDownLatch(1);
            stopWatch.start();
            bluetoothAdapter.enable();
            awaitLatch(btEnabledLatch, TimeConstant.NETWORK_LATCH_TIME_WAIT);
            stopWatch.stopAndPrintSeconds("Enable BT");
        }

        if (!bluetoothAdapter.isEnabled()) {
            Logcat.e(TAG, "BT could not be enabled");
            androidUtils.unregisterReceiverQuietly(receiver);
            return;
        }

        /**
         * This seems to be needed as even though we enable BT
         * background events still fire and if we set the BT name too early
         * it gets set back to the original device name
         */
        sleepQuietly(TimeConstant.RACE_CONDITION_WAIT_2S);

        this.stopAdvertisingLatch = new CountDownLatch(1);
        if(registerLEService(subscriber)) {
            ConcurrencyUtils.awaitLatch(stopAdvertisingLatch);
            Logcat.v(TAG, "Stop advertising latch done");
        }
        //Stop Broadcast will be called from parent once we get here
    }


    @AnyThread
    public void stopBroadcast() {
        androidUtils.unregisterReceiverQuietly(receiver);
        BluetoothLeAdvertiser bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();

        if (bluetoothLeAdvertiser != null && advertisingCallback != null) {
            bluetoothLeAdvertiser.stopAdvertising(advertisingCallback);
        }

        if (btEnabledLatch != null) {
            btEnabledLatch.countDown();
        }

        if (btDisableLatch != null) {
            btDisableLatch.countDown();
        }

        if (deviceNameLatch != null) {
            deviceNameLatch.countDown();
        }

        if (stopAdvertisingLatch != null) {
            Logcat.v(TAG, "Counting down stop adv latch");
            stopAdvertisingLatch.countDown();
        }

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

    private boolean registerLEService(Subscriber<? super PostmanBroadcastEvent> subscriber) {
        deviceNameLatch = new CountDownLatch(1);
        stopWatch.start();

        if (!tryAction(() -> bluetoothAdapter.setName(deviceNameToBroadcast), TimeConstant.RETRY_DELAY, RETRY_COUNT)) {
            Logcat.e(TAG, "Device name cannot be set");
            subscriber.onError(new PostmanBroadcastException("Could not set BT Device Name"));
            return false;
        }

        awaitLatch(deviceNameLatch, TimeConstant.RACE_CONDITION_WAIT_2S);
        stopWatch.stopAndPrintSeconds("Set BT Device Name");

        if (!bluetoothAdapter.getName().equals(deviceNameToBroadcast)) {
            Logcat.e(TAG, "BT Device name change Failed");
            subscriber.onError(new PostmanBroadcastException("Device name not as expected"));
            return false;
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
        stopWatch.start();
        advertisingCallback = new BTAdvertisingCallback(subscriber);
        advertiser.startAdvertising(advertiseSettings, advertiseData, advertisingCallback);
        return true;
    }

    private class BTAdvertisingCallback extends AdvertiseCallback {

        private final Subscriber<? super PostmanBroadcastEvent> subscriber;

        BTAdvertisingCallback(Subscriber<? super PostmanBroadcastEvent> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            stopWatch.stopAndPrintSeconds("Start Advertising");
            Logcat.d(TAG, "Registered advertising data successfully");
            Logcat.v(TAG, settingsInEffect.toString());
            Logcat.v(TAG, "DeviceName=%s", bluetoothAdapter.getName());
            subscriber.onNext(PostmanBroadcastEvent.broadcastStarted());
        }

        @SuppressLint("DefaultLocale")
        @Override
        public void onStartFailure(int errorCode) {
            Logcat.e(TAG, "Start advertising failure=", translateErrorCode(errorCode));
            stopWatch.stopAndPrintSeconds("Start Advertising Failure");
            subscriber.onError(new PostmanBroadcastException(String.format("Could not begin BT Advertising errorCode=%s", translateErrorCode(errorCode))));
            stopBroadcast();
        }

        private String translateErrorCode(int errorCode) {
            switch (errorCode) {
                case AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED:
                    return "Already started";
                case AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE:
                    return "Data too large";
                case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                    return "Feature Unsupported";
                case ADVERTISE_FAILED_INTERNAL_ERROR:
                    return "Internal Error";
                case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                    return "Too many advertisers";
                default:
                    return "Unknown";

            }
        }
    }

    private class BluetoothStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (bluetoothEnabled(intent)) {
                Logcat.d(TAG, "Bluetooth enabled");
                btEnabledLatch.countDown();
            } else if (bluetoothDisabled(intent)) {
                Logcat.d(TAG, "Bluetooth disabled");
                btDisableLatch.countDown();
            } else if (nameChanged(intent)) {
                Logcat.d(TAG, "Device name changed %s", bluetoothAdapter.getName());
                deviceNameLatch.countDown();
            }
        }

        private boolean bluetoothDisabled(Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, Integer.MIN_VALUE);
                return state == BluetoothAdapter.STATE_OFF && !bluetoothAdapter.isEnabled();
            }
            return false;
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

//    Look at using this for serviceId identification
//
//    int serviceUuid = 0xFEAA;
//    byte[] serviceUuidBytes = new byte[] {
//            (byte) (serviceUuid & 0xff),
//            (byte) ((serviceUuid >> 8) & 0xff)};
//    ParcelUuid parcelUuid = parseUuidFrom(serviceUuidBytes);
//
//
//    /**
//     * Parse UUID from bytes. The {@code uuidBytes} can represent a 16-bit, 32-bit or 128-bit UUID,
//     * but the returned UUID is always in 128-bit format.
//     * Note UUID is little endian in Bluetooth.
//     *
//     * @param uuidBytes Byte representation of uuid.
//     * @return {@link ParcelUuid} parsed from bytes.
//     * @throws IllegalArgumentException If the {@code uuidBytes} cannot be parsed.
//     *
//     * Copied from java/android/bluetooth/BluetoothUuid.java
//     * Copyright (C) 2009 The Android Open Source Project
//     * Licensed under the Apache License, Version 2.0
//     */
//    private static ParcelUuid parseUuidFrom(byte[] uuidBytes) {
//        /** Length of bytes for 16 bit UUID */
//        final int UUID_BYTES_16_BIT = 2;
//        /** Length of bytes for 32 bit UUID */
//        final int UUID_BYTES_32_BIT = 4;
//        /** Length of bytes for 128 bit UUID */
//        final int UUID_BYTES_128_BIT = 16;
//        final ParcelUuid BASE_UUID =
//                ParcelUuid.fromString("00000000-0000-1000-8000-00805F9B34FB");
//        if (uuidBytes == null) {
//            throw new IllegalArgumentException("uuidBytes cannot be null");
//        }
//        int length = uuidBytes.length;
//        if (length != UUID_BYTES_16_BIT && length != UUID_BYTES_32_BIT &&
//                length != UUID_BYTES_128_BIT) {
//            throw new IllegalArgumentException("uuidBytes length invalid - " + length);
//        }
//        // Construct a 128 bit UUID.
//        if (length == UUID_BYTES_128_BIT) {
//            ByteBuffer buf = ByteBuffer.wrap(uuidBytes).order(ByteOrder.LITTLE_ENDIAN);
//            long msb = buf.getLong(8);
//            long lsb = buf.getLong(0);
//            return new ParcelUuid(new UUID(msb, lsb));
//        }
//        // For 16 bit and 32 bit UUID we need to convert them to 128 bit value.
//        // 128_bit_value = uuid * 2^96 + BASE_UUID
//        long shortUuid;
//        if (length == UUID_BYTES_16_BIT) {
//            shortUuid = uuidBytes[0] & 0xFF;
//            shortUuid += (uuidBytes[1] & 0xFF) << 8;
//        } else {
//            shortUuid = uuidBytes[0] & 0xFF ;
//            shortUuid += (uuidBytes[1] & 0xFF) << 8;
//            shortUuid += (uuidBytes[2] & 0xFF) << 16;
//            shortUuid += (uuidBytes[3] & 0xFF) << 24;
//        }
//        long msb = BASE_UUID.getUuid().getMostSignificantBits() + (shortUuid << 32);
//        long lsb = BASE_UUID.getUuid().getLeastSignificantBits();
//        return new ParcelUuid(new UUID(msb, lsb));
//    }