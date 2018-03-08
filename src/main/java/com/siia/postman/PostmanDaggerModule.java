package com.siia.postman;

import android.net.nsd.NsdManager;

import com.siia.commons.core.android.AndroidDaggerModule;
import com.siia.commons.core.rx.SchedulersModule;
import com.siia.postman.discovery.AndroidNsdDiscoveryService;
import com.siia.postman.discovery.PostmanDiscoveryService;
import com.siia.postman.server.PostmanClient;
import com.siia.postman.server.PostmanMessage;
import com.siia.postman.server.PostmanServer;
import com.siia.postman.server.nio.NIOPostmanClient;
import com.siia.postman.server.nio.NIOPostmanServer;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import io.reactivex.Scheduler;

@Module(includes = {AndroidDaggerModule.class, SchedulersModule.class})
class PostmanDaggerModule {

    @Provides
    @Singleton
    PostmanServer postmanServer(Provider<PostmanMessage> messageProvider){
        return new NIOPostmanServer(messageProvider);
    }

    @Provides
    @Singleton
    PostmanDiscoveryService postmanDiscoveryService(NsdManager nsdManager){
        return new AndroidNsdDiscoveryService(nsdManager);
    }

    @Provides
    @Singleton
    PostmanClient providesPostmanClient(@Named("computation") Scheduler scheduler,
                                        @Named("io") Scheduler ioScheduler,
                                        Provider<PostmanMessage> messageProvider){
        return new NIOPostmanClient(scheduler, messageProvider, ioScheduler);
    }
}

