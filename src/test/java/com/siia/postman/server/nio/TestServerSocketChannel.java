package com.siia.postman.server.nio;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

public class TestServerSocketChannel extends ServerSocketChannel {

    private final ServerSocket mockSocket;
    private final SocketChannel mockSocketChannel;
    public boolean blocking;
    public boolean closed;
    public boolean throwOnAccept;

    protected TestServerSocketChannel(ServerSocket mockSocket, SelectorProvider provider, SocketChannel mockSocketChannel) {
        super(provider);
        this.mockSocket = mockSocket;
        this.mockSocketChannel = mockSocketChannel;
    }

    @Override
    public ServerSocketChannel bind(SocketAddress local, int backlog) throws IOException {
        return null;
    }

    @Override
    public <T> ServerSocketChannel setOption(SocketOption<T> name, T value) throws IOException {
        return null;
    }

    @Override
    public <T> T getOption(SocketOption<T> name) throws IOException {
        return null;
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return null;
    }

    @Override
    public ServerSocket socket() {
        return mockSocket;
    }

    @Override
    public SocketChannel accept() throws IOException {
        if(throwOnAccept) {
            throw new IOException();
        }
        return mockSocketChannel;
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return null;
    }

    @Override
    protected void implCloseSelectableChannel() throws IOException {
        closed = true;
    }

    @Override
    protected void implConfigureBlocking(boolean block) throws IOException {
        blocking = block;
    }


}
