package com.siia.postman.server.nio;

import android.support.annotation.NonNull;

import com.siia.commons.core.io.IO;
import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.Connection;
import com.siia.postman.server.PostmanMessage;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Provider;

import static com.siia.commons.core.log.Logcat.w;

/**
 * Copyright Siia 2018
 */
class NIOConnectionFactory {
    private static final String TAG = Logcat.getTag();
    private final Provider<PostmanMessage> messageProvider;

    @Inject
    NIOConnectionFactory(Provider<PostmanMessage> messageProvider) {
        this.messageProvider = messageProvider;
    }

    Optional<NIOConnection> acceptConnection(@NonNull ServerSocketChannel serverSocketChannel, Selector nioSelector) {
        SocketChannel clientSocketChannel = null;
        try {
            SelectionKey clientKey;
            clientSocketChannel = serverSocketChannel.accept();
            clientSocketChannel.socket().setKeepAlive(true);
            clientSocketChannel.socket().setPerformancePreferences(Connection.CONNECTION_TIME_PREFERENCE,
                    Connection.LATENCY_PREFERENCE, Connection.BANDWIDTH_PREFERENCE);
            clientSocketChannel.configureBlocking(false);
            clientKey = clientSocketChannel.register(nioSelector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);
            return  Optional.of(new NIOConnection(clientSocketChannel, messageProvider, clientKey));
        } catch (Throwable e) {
            w(TAG, "Couldnt accept connection channel", e);
            IO.closeQuietly(clientSocketChannel);
            return Optional.empty();
        }

    }
}
