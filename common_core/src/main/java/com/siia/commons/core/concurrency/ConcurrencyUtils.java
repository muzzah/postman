package com.siia.commons.core.concurrency;

import com.siia.commons.core.constants.TimeConstant;
import com.siia.commons.core.log.Logcat;

import java.util.concurrent.CountDownLatch;
import java.util.function.BooleanSupplier;

/**
 * Copyright Siia 2018
 */

public final class ConcurrencyUtils {


    private static final String TAG = Logcat.getTag();

    private ConcurrencyUtils() {
        //Ignore
    }

    public static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Logcat.e(TAG, "Problem waiting for latch", e);
        }
    }

    public static void awaitLatch(CountDownLatch latch, TimeConstant timeConstant) {
        try {
            latch.await(timeConstant.val(), timeConstant.getTimeUnit());
        } catch (InterruptedException e) {
            Logcat.e(TAG, "Problem waiting for latch", e);
        }
    }

    public static void sleepQuietly(TimeConstant timeConstant) {
        try {
            timeConstant.getTimeUnit().sleep(timeConstant.val());
        } catch (Exception e) {
            Logcat.w(TAG, "Problem when sleeping", e);
        }
    }

    public static boolean tryAction(BooleanSupplier action, TimeConstant timeConstant, int times) {
        int count = 0;
        while(!action.getAsBoolean()) {
            Logcat.w(TAG, "Action failed");
            if(count == times) {
                Logcat.w(TAG, "Failed to perform action successfully");
                return false;

            }
            count++;
            sleepQuietly(timeConstant);
        }
        return true;

    }
}
