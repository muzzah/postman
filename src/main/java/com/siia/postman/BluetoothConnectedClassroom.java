package com.siia.postman;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.ParcelUuid;

import com.osiyent.sia.commons.core.log.Logcat;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

public class BluetoothConnectedClassroom implements Classroom {
    private static final String TAG = Logcat.getTag();
    private final PackageManager packageManager;
    private final Context context;

    @Inject
    public BluetoothConnectedClassroom(PackageManager packageManager, Context context) {
        this.packageManager = packageManager;
        this.context = context;
    }

    @Override
    public void startClass() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Logcat.e(TAG, "BLE not available, postman not started");
            return;
        }
        final BluetoothManager bluetoothManager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        BluetoothLeAdvertiser advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (!bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.enable();
        }

        AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setTimeout(30 * 1000)
                .build();

        ParcelUuid uuid = new ParcelUuid(new UUID(1L,1L));
        AdvertiseData advertiseData = new AdvertiseData.Builder().addServiceUuid(uuid).addServiceData(uuid, );
        advertiser.startAdvertising(advertiseSettings,);
    }
}
