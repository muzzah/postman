package com.siia.postman.server.nio;

import android.support.annotation.NonNull;

import com.siia.commons.core.io.IO;
import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.Connection;
import com.siia.postman.server.PostmanMessage;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Provider;

import static com.siia.commons.core.log.Logcat.v;
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

    SelectionKey bindServerSocket(Selector nioSelector, ServerSocketChannel serverSocketChannel, InetSocketAddress bindAddress) throws IOException {
        ServerSocket serverSocket = serverSocketChannel.socket();
        serverSocket.setPerformancePreferences(Connection.CONNECTION_TIME_PREFERENCE,
                Connection.LATENCY_PREFERENCE, Connection.BANDWIDTH_PREFERENCE);
        serverSocket.bind(bindAddress);
        v(TAG, "Server bound to %s:%d", bindAddress.getAddress().getHostAddress(), serverSocket.getLocalPort());
        serverSocketChannel.configureBlocking(false);
        return serverSocketChannel.register(nioSelector,
                SelectionKey.OP_ACCEPT);
    }

    NIOConnection connectToServer(Selector selector, SocketChannel socketChannel, InetAddress serverAddress, int port) throws IOException {
        socketChannel.socket().setKeepAlive(true);
        socketChannel.socket().setPerformancePreferences(Connection.CONNECTION_TIME_PREFERENCE,
                Connection.LATENCY_PREFERENCE, Connection.BANDWIDTH_PREFERENCE);

        if (!socketChannel.connect(new InetSocketAddress(serverAddress, port))) {
            Logcat.d(TAG, "connect return false, still connecting possibly");
        }

        socketChannel.configureBlocking(false);
        SelectionKey clientKey = socketChannel.register(selector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);
        return new NIOConnection(socketChannel, messageProvider, clientKey);
    }
}
