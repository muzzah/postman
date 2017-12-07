package com.siia.postman;

import android.net.nsd.NsdManager;

import com.siia.commons.core.android.AndroidDaggerModule;
import com.siia.commons.core.rx.SchedulersModule;
import com.siia.postman.classroom.ClassroomOperations;
import com.siia.postman.discovery.AndroidNsdDiscoveryService;
import com.siia.postman.discovery.PostmanDiscoveryService;
import com.siia.postman.server.PostmanClient;
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
    ClassroomOperations providesClassroom(PostmanServer server, PostmanDiscoveryService discoveryService,
                                          @Named("computation") Scheduler computation, Provider<PostmanClient> clientProvider) {
        return new ClassroomOperations(server, discoveryService, clientProvider, computation);
    }


    @Provides
    PostmanServer postmanServer(){
        return new NIOPostmanServer();
    }

    @Provides
    PostmanDiscoveryService postmanDiscoveryService(NsdManager nsdManager){
        return new AndroidNsdDiscoveryService(nsdManager);
    }

    @Provides
    PostmanClient providesPostmanClient(@Named("computation") Scheduler scheduler){
        return new NIOPostmanClient(scheduler);
    }
}
