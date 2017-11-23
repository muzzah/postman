package com.siia.postman.discovery;

import android.net.nsd.NsdManager;

import com.osiyent.sia.commons.core.android.AndroidDaggerModule;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module(includes = AndroidDaggerModule.class)
public class PostmanDiscoveryDaggerModule {

    @Provides
    @Singleton
    public PostmanDiscoveryService providesPostmanDiscoveryService(NsdManager nsdManager) {
        return new AndroidNdsDiscoveryService(nsdManager);
    }
}
