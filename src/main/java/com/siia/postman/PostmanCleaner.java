package com.siia.postman;

import com.osiyent.sia.commons.core.android.Cleaner;
import com.osiyent.sia.commons.core.log.Logcat;
import com.siia.postman.discovery.PostmanDiscoveryService;

import javax.inject.Inject;

public class PostmanCleaner implements Cleaner {

    private static final String TAG = Logcat.getTag();
    private PostmanDiscoveryService discoveryService;

    @Inject
    public PostmanCleaner(PostmanDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @Override
    public void cleanup() {
        Logcat.d(TAG, "Cleaning up Postman");
        /**
         * If Service is registered for broadcasting but app crashes
         * we need to stop service broadcasting otherwise when re-registering
         * on next launch, clients discovering wont be notified
         */
        discoveryService.stopServiceBroadcast();
    }

}
