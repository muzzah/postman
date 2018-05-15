package com.siia.postman.server.nio;

import com.siia.commons.core.rx.SchedulersModule;
import com.siia.postman.server.PostmanClient;
import com.siia.postman.server.PostmanServer;

import java.nio.channels.spi.SelectorProvider;

import javax.inject.Named;

import dagger.Module;
import dagger.Provides;
import io.reactivex.Scheduler;

/**
 * Copyright Siia 2018
 */
@Module(includes = SchedulersModule.class)
public class NIODaggerModule {

    @Provides
    PostmanServer postmanServer(ServerEventLoop serverEventLoop) {
        return new NIOPostmanServer(serverEventLoop);
    }

    @Provides
    PostmanClient providesPostmanClient(SelectorProvider selectorProvider,
                                        @Named("new") Scheduler newThreadScheduler,
                                        NIOConnectionFactory nioConnectionFactory) {
        return new NIOPostmanClient(newThreadScheduler, selectorProvider, nioConnectionFactory);
    }

    @Provides
    SelectorProvider providesSelectorProvider() {
        return SelectorProvider.provider();
    }
}
