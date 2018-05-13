package com.siia.postman;

import com.siia.commons.core.android.AndroidDaggerModule;
import com.siia.commons.core.rx.SchedulersModule;
import com.siia.postman.discovery.BluetoothBroadcaster;
import com.siia.postman.discovery.BluetoothDiscoverer;
import com.siia.postman.discovery.BluetoothPostmanDiscoveryService;
import com.siia.postman.discovery.BonjourDiscoveryService;
import com.siia.postman.discovery.PostmanDiscoveryService;
import com.siia.postman.server.nio.NIODaggerModule;

import javax.inject.Named;

import dagger.Module;
import dagger.Provides;
import io.reactivex.Scheduler;

@Module(
        includes = {
                NIODaggerModule.class,
                AndroidDaggerModule.class,
                SchedulersModule.class
        }
)
class PostmanDaggerModule {


    @Provides
    @Named("network")
    PostmanDiscoveryService nsdDiscoveryService(@Named("io") Scheduler io) {
        return new BonjourDiscoveryService(io);
    }

    @Provides
    @Named("bt")
    PostmanDiscoveryService btDiscoveryService(BluetoothBroadcaster bluetoothBroadcaster,
                                               BluetoothDiscoverer bluetoothDiscoverer,
                                               @Named("io") Scheduler io) {
        return new BluetoothPostmanDiscoveryService(bluetoothBroadcaster, bluetoothDiscoverer, io);
    }


}

