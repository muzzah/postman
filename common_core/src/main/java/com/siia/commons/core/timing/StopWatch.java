package com.siia.commons.core.timing;

import android.support.annotation.NonNull;

import com.siia.commons.core.log.Logcat;

import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;

/**
 * Copyright Siia 2018
 */

public class StopWatch {
    private static final String TAG = Logcat.getTag();
    private static final DecimalFormat FORMATTER = new DecimalFormat("#.##");

    private long startTime;
    private long endTime;

    public void start() {
        startTime = endTime = System.nanoTime();
    }

    public void stopAndPrintSeconds(@NonNull String action) {
        endTime = System.nanoTime();

        printSummarySeconds(action);
    }

    public void stopAndPrintMillis(@NonNull String action) {
        endTime = System.nanoTime();
        printSummaryMillis(action);
    }

    private void printSummarySeconds(@NonNull String action) {
        Logcat.d(TAG, "%s totalTime(sec)=%s", action, FORMATTER.format((double) TimeUnit.NANOSECONDS.toSeconds(endTime-startTime)));
    }

    private void printSummaryMillis(@NonNull String action) {
        Logcat.d(TAG, "%s totalTime(ms)=%d", action, TimeUnit.NANOSECONDS.toMillis(endTime-startTime));
    }

}
