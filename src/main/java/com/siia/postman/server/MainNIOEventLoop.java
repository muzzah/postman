package com.siia.postman.server;

import android.util.Log;

import com.osiyent.sia.commons.core.io.IO;
import com.osiyent.sia.commons.core.log.Logcat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import rx.Observable.OnSubscribe;
import rx.Subscriber;

import static com.osiyent.sia.commons.core.log.Logcat.d;
import static com.osiyent.sia.commons.core.log.Logcat.i;
import static com.osiyent.sia.commons.core.log.Logcat.v;
import static com.osiyent.sia.commons.core.log.Logcat.w;

class MainNIOEventLoop implements OnSubscribe<ByteBuffer> {
    private static final String TAG = Logcat.getTag();
    private static final int BUFFER_SIZE = 4096;

    private enum ChannelType {
        SERVER,
        CLIENT
    }

    private ServerSocketChannel serverSocketChannel;
    private Selector selector;
    private final InetSocketAddress bindAddress;

    public MainNIOEventLoop(InetSocketAddress bindAddress) {
        this.bindAddress = bindAddress;
    }

    public void shutdownLoop() {
        IO.closeQuietly(selector);
        IO.closeQuietly(serverSocketChannel);
        //Close client connections
    }


    @Override
    public void call(Subscriber<? super ByteBuffer> subscriber) {
        i(TAG, "Starting NIO Based event loop");
        try {
            initialiseServerSocket();
            while (true) {
                d(TAG, "Waiting for channels to become available");
                int channelsReady = selector.select();
                if(!selector.isOpen()){
                    subscriber.onCompleted();
                    break;
                }

                d(TAG, "%s channels ready", channelsReady);
                for (SelectionKey key : selector.selectedKeys()) {
                    ChannelType channelType = (ChannelType) key.attachment();
                    switch (channelType) {
                        case CLIENT:
                            processClientConnection(key);
                            break;
                        case SERVER:
                            acceptClientConnection();
                            break;
                        default:
                            Logcat.e(TAG, "Unrecognised channel type : %s", channelType);
                    }

                }

            }

            subscriber.onCompleted();
        } catch (Exception e) {
            subscriber.onError(e);
        } finally {
            IO.closeQuietly(selector);
            IO.closeQuietly(serverSocketChannel);
        }

    }

    private void processClientConnection(SelectionKey clientSelectionKey) {
        if (clientSelectionKey.isReadable()) {
            v(TAG, "Reading from client connection");
            SocketChannel clientChannel = (SocketChannel) clientSelectionKey.channel();

            //What is allocatedDirect?
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

            try {
                int bytesRead = clientChannel.read(buffer);
                if (bytesRead == -1) {
                    w(TAG, "Client channel closed");
                    IO.closeQuietly(clientChannel);
                    clientSelectionKey.cancel();
                } else {
                    v(TAG, "Read %d bytes", bytesRead);
                    buffer.flip();
                    clientChannel.write(buffer);
                }

            } catch (Exception e) {
                w(TAG, "Error when reading from client", e);
                if (!clientChannel.isConnected()) {
                    //Remove from client pool
                    clientSelectionKey.cancel();
                    IO.closeQuietly(clientChannel);

                }
            }
        }
    }


    public void initialiseServerSocket() throws IOException {
        i(TAG, "Opening Server Channel");
        selector = Selector.open();
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(bindAddress);
        serverSocketChannel.configureBlocking(false);
        d(TAG, "Registering server channel with selector");
        SelectionKey socketServerSelectionKey = serverSocketChannel.register(selector,
                SelectionKey.OP_ACCEPT);
        socketServerSelectionKey.attach(ChannelType.SERVER);
    }

    private void acceptClientConnection() {
        i(TAG, "Accepting new client channel");
        SocketChannel clientSocketChannel = null;
        SelectionKey clientKey = null;
        try {
            clientSocketChannel = serverSocketChannel.accept();

            if (clientSocketChannel != null) {
                clientSocketChannel.configureBlocking(false);
                d(TAG, "Registering client channel with selector");
                clientKey = clientSocketChannel.register(selector, SelectionKey.OP_READ);
                clientKey.attach(ChannelType.CLIENT);
            } else {
                Log.w(TAG, "Client channel is null");
            }
        } catch (IOException e) {
            w(TAG, "Couldnt accept client channel", e);
            if (clientKey != null) {
                clientKey.cancel();
            }
            IO.closeQuietly(clientSocketChannel);
        }

    }
}