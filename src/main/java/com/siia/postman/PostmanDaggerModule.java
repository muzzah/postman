package com.siia.postman;

import android.net.nsd.NsdManager;

import com.siia.commons.core.android.AndroidDaggerModule;
import com.siia.commons.core.rx.SchedulersModule;
import com.siia.postman.discovery.AndroidNsdDiscoveryService;
import com.siia.postman.discovery.BluetoothBroadcaster;
import com.siia.postman.discovery.BluetoothDiscoverer;
import com.siia.postman.discovery.BluetoothPostmanDiscoveryService;
import com.siia.postman.discovery.PostmanDiscoveryService;
import com.siia.postman.server.PostmanClient;
import com.siia.postman.server.PostmanMessage;
import com.siia.postman.server.PostmanServer;
import com.siia.postman.server.nio.NIOPostmanClient;
import com.siia.postman.server.nio.NIOPostmanServer;

import javax.inject.Named;
import javax.inject.Provider;

import dagger.Module;
import dagger.Provides;
import io.reactivex.Scheduler;

@Module(includes = {AndroidDaggerModule.class, SchedulersModule.class})
class PostmanDaggerModule {

    @Provides
    PostmanServer postmanServer(Provider<PostmanMessage> messageProvider,
                                @Named("computation") Scheduler computation,
                                @Named("new") Scheduler newThreadScheduler,
                                @Named("io") Scheduler io){
        return new NIOPostmanServer(messageProvider, computation, io, newThreadScheduler);
    }

    @Provides
    @Named("nsd")
    PostmanDiscoveryService nsdDiscoveryService(NsdManager nsdManager, @Named("io") Scheduler io){
            return new AndroidNsdDiscoveryService(nsdManager, io);
    }

    @Provides
    @Named("bt")
    PostmanDiscoveryService btDiscoveryService(BluetoothBroadcaster bluetoothBroadcaster,
                                               BluetoothDiscoverer bluetoothDiscoverer,
                                               @Named("io") Scheduler io){
        return new BluetoothPostmanDiscoveryService(bluetoothBroadcaster, bluetoothDiscoverer, io);
    }

    @Provides
    PostmanClient providesPostmanClient(@Named("computation") Scheduler computation,
                                        @Named("io") Scheduler ioScheduler,
                                        @Named("new") Scheduler newThreadScheduler,
                                        Provider<PostmanMessage> messageProvider){
        return new NIOPostmanClient(computation, messageProvider, ioScheduler, newThreadScheduler);
    }
}

