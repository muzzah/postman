package com.siia.postman.server;

import dagger.Module;
import dagger.Provides;

@Module
public class PostmanServerDaggerModule {

    @Provides
    public PostmanServer providesPostmanServer() {
        return new IPPostmanServer();
    }
}
