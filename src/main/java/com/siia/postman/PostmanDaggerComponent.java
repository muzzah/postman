package com.siia.postman;

import com.siia.postman.discovery.PostmanDiscoveryService;
import com.siia.postman.server.PostmanClient;
import com.siia.postman.server.PostmanServer;
import com.siia.postman.server.nio.NIODaggerModule;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Component;

@Component(
        modules = {
                NIODaggerModule.class,
                PostmanDaggerModule.class
        }
)
@Singleton
public interface PostmanDaggerComponent {

    PostmanServer postmanServer();

    PostmanClient postmanClientProvider();

    @Named("network")
    PostmanDiscoveryService nsdDiscoveryService();

    @Named("bt")
    PostmanDiscoveryService btDiscoveryService();
}
