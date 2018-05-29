package org.postman.discovery;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import com.siia.commons.core.constants.TimeConstant;
import com.siia.commons.core.log.Logcat;
import com.siia.commons.core.timing.StopWatch;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import io.reactivex.FlowableEmitter;

import static com.siia.commons.core.concurrency.ConcurrencyUtils.awaitLatch;
import static java.util.Objects.nonNull;

public class BluetoothDiscoverer {

    private static final String TAG = Logcat.getTag();
    private final BluetoothAdapter bluetoothAdapter;
    private final Context ctx;
    private final StopWatch stopWatch;
    private CountDownLatch bluetoothEnabledLatch;
    private CountDownLatch finishScanningLatch;
    private BluetoothStateReceiver receiver;
    private ScanCallback leScanCallback;

    BluetoothDiscoverer(BluetoothAdapter bluetoothAdapter, Context ctx, StopWatch stopWatch) {
        this.bluetoothAdapter = bluetoothAdapter;
        this.ctx = ctx;
        this.stopWatch = stopWatch;
    }

    @WorkerThread
    public void findService(@NonNull String nameToFind, FlowableEmitter<PostmanDiscoveryEvent> subscriber) {

        registerReceiver();
        if (!bluetoothAdapter.isEnabled()) {

            bluetoothEnabledLatch = new CountDownLatch(1);
            stopWatch.start();
            boolean enable = bluetoothAdapter.enable();
            Logcat.result_v(TAG, "enable bluetooth", enable);

            if (!enable) {
                stopWatch.stopAndPrintMillis("Enable BT Failed");
                stopDiscovery();
                subscriber.onError(new PostmanDiscoveryException("Could not enable bluetooth"));
                return;
            }

            awaitLatch(bluetoothEnabledLatch, TimeConstant.NETWORK_LATCH_TIME_WAIT);
            stopWatch.stopAndPrintMillis("Enable BT Success");
        }

        if (!bluetoothAdapter.isEnabled()) {
            stopDiscovery();
            subscriber.onError(new PostmanDiscoveryException("Bluetooth not enabled"));
            return;
        }

        finishScanningLatch = new CountDownLatch(1);
        stopWatch.start();
        startDiscovery(nameToFind, subscriber);
        awaitLatch(finishScanningLatch);
        stopWatch.stopAndPrintSeconds("Discover Service completed");
    }

    @WorkerThread
    public void stopDiscovery() {

        unregisterReceiverQuietly(receiver);

        if (nonNull(bluetoothEnabledLatch)) {
            bluetoothEnabledLatch.countDown();
        }
        if (nonNull(finishScanningLatch)) {
            finishScanningLatch.countDown();
        }
        BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        if (nonNull(bluetoothLeScanner)) {
            bluetoothLeScanner.stopScan(leScanCallback);
        }

        if (bluetoothAdapter.isEnabled()) {
            Logcat.result_v(TAG, "Disabling Adapter", bluetoothAdapter.disable());
        }
    }

    private void unregisterReceiverQuietly(BluetoothStateReceiver receiver) {
        try {
            ctx.unregisterReceiver(receiver);
        } catch(Throwable e) {
            //Ignore
        }
    }


    private void startDiscovery(String nameToFind, FlowableEmitter<PostmanDiscoveryEvent> subscriber) {

        BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
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
        ctx.registerReceiver(receiver, filter);
    }

    private class LeScanCallback extends ScanCallback {
        private final CharSequence nameToFind;
        private final FlowableEmitter<PostmanDiscoveryEvent> subscriber;

        private LeScanCallback(CharSequence nameToFind, FlowableEmitter<PostmanDiscoveryEvent> subscriber) {
            this.nameToFind = nameToFind;
            this.subscriber = subscriber;
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Logcat.v(TAG, "Found device %s", result.toString());

            BluetoothDevice device = result.getDevice();

            if (nonNull(device) && nonNull(device.getName()) && device.getName().contains(nameToFind)) {
                Logcat.d(TAG, "Found device : %s", device.getName());

                if(subscriber.isCancelled()) {
                    stopDiscovery();
                    return;
                }

                subscriber.onNext(PostmanDiscoveryEvent.found(new ServiceDetails(device.getName())));
            }

        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            results.forEach(scanResult -> onScanResult(0, scanResult));
        }

        @Override
        public void onScanFailed(int errorCode) {
            Logcat.e(TAG, "Problem when scanning for devices %d", errorCode);
            subscriber.onError(new PostmanDiscoveryException("BT problem when scanning for devices"));
            stopDiscovery();


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
}
