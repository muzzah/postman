package com.siia.postman;

import com.siia.postman.discovery.PostmanDiscoveryService;
import com.siia.postman.server.PostmanServer;

import javax.inject.Singleton;

import dagger.Component;

@Component(modules = PostmanDaggerModule.class)
@Singleton
public interface PostmanDaggerComponent {
    Postman createPostman();
    PostmanServer createPostmanServer();
    PostmanDiscoveryService createPostmanDiscoveryService();
}
