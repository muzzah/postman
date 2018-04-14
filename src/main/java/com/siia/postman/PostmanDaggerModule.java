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
    PostmanServer postmanServer(Provider<PostmanMessage> messageProvider, @Named("computation") Scheduler computation,
                                @Named("computation") Scheduler io){
        return new NIOPostmanServer(messageProvider, computation, io);
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
                                               @Named("computation") Scheduler computation){
        return new BluetoothPostmanDiscoveryService(bluetoothBroadcaster, bluetoothDiscoverer, computation);
    }

    @Provides
    PostmanClient providesPostmanClient(@Named("computation") Scheduler scheduler,
                                        @Named("io") Scheduler ioScheduler,
                                        Provider<PostmanMessage> messageProvider){
        return new NIOPostmanClient(scheduler, messageProvider, ioScheduler);
    }
}

