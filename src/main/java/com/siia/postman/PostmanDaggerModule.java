package com.siia.postman;

import android.net.nsd.NsdManager;

import com.siia.commons.core.android.AndroidDaggerModule;
import com.siia.postman.classroom.ClassroomOperations;
import com.siia.postman.discovery.AndroidNsdDiscoveryService;
import com.siia.postman.discovery.PostmanDiscoveryService;
import com.siia.postman.server.nio.NIOPostmanServer;
import com.siia.postman.server.PostmanServer;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module(includes = AndroidDaggerModule.class)
public class PostmanDaggerModule {

    @Provides
    @Singleton
    ClassroomOperations providesClassroom(PostmanServer server, PostmanDiscoveryService discoveryService) {
        return new ClassroomOperations(server, discoveryService);
    }


    @Provides
    PostmanServer postmanServer(){
        return new NIOPostmanServer();
    }

    @Provides
    PostmanDiscoveryService postmanDiscoveryService(NsdManager nsdManager){
        return new AndroidNsdDiscoveryService(nsdManager);
    }
}
