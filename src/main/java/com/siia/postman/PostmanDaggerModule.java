package com.siia.postman;

import com.osiyent.sia.commons.core.android.AndroidDaggerModule;
import com.siia.postman.discovery.PostmanDiscoveryDaggerModule;
import com.siia.postman.discovery.PostmanDiscoveryService;
import com.siia.postman.server.PostmanServer;
import com.siia.postman.server.PostmanServerDaggerModule;

import dagger.Module;
import dagger.Provides;

@Module(includes = {AndroidDaggerModule.class, PostmanServerDaggerModule.class, PostmanDiscoveryDaggerModule.class})
public class PostmanDaggerModule {


    @Provides Postman providesPostman(PostmanServer postmanServer, PostmanDiscoveryService postmanDiscoveryService) {
        return new Postman(postmanServer, postmanDiscoveryService);
    }
}
