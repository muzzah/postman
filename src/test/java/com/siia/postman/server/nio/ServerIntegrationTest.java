package com.siia.postman.server.nio;

import com.siia.postman.server.PostmanMessage;

import org.junit.Before;

import java.nio.channels.spi.SelectorProvider;

import io.reactivex.schedulers.Schedulers;

/**
 * Copyright Siia 2018
 */
public class ServerIntegrationTest {
    private NIOPostmanServer postmanServer;

    @Before
    public void setup() {
        postmanServer = new NIOPostmanServer(new ServerEventLoop(SelectorProvider.provider(), new NIOConnectionFactory(PostmanMessage::new), Schedulers.newThread()));
    }

}
