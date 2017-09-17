package com.siia.postman.server;

import android.annotation.SuppressLint;

import com.osiyent.sia.commons.core.io.IO;
import com.osiyent.sia.commons.core.log.Logcat;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;

import static com.osiyent.sia.commons.core.log.Logcat.d;
import static com.osiyent.sia.commons.core.log.Logcat.i;
import static com.osiyent.sia.commons.core.log.Logcat.v;
import static com.osiyent.sia.commons.core.log.Logcat.w;

class MainNIOEventLoop implements Publisher<NetworkEvent> {
    private static final String TAG = Logcat.getTag();
    private Subscriber<? super NetworkEvent> subscriber;


    private enum ChannelType {
        SERVER,
        CLIENT
    }

    private ServerSocketChannel serverSocketChannel;
    private Selector selector;
    private final InetSocketAddress bindAddress;
    private final Map<Integer, Client> connectedClients;

    @SuppressLint("UseSparseArrays")
    MainNIOEventLoop(InetSocketAddress bindAddress) {
        this.bindAddress = bindAddress;
        connectedClients = Collections.synchronizedMap(new HashMap<Integer, Client>());
    }

    void shutdownLoop() {
        IO.closeQuietly(selector);
        IO.closeQuietly(serverSocketChannel.socket());
        IO.closeQuietly(serverSocketChannel);
        //Close client connections
    }

    boolean isRunning() {
        return serverSocketChannel != null && serverSocketChannel.isOpen() && !serverSocketChannel.socket().isClosed();
    }



    @Override
    public void subscribe(Subscriber<? super NetworkEvent> subscriber) {

        i(TAG, "Starting NIO Based event loop");
        this.subscriber = subscriber;
        try {
            initialiseServerSocket();
            subscriber.onNext(NetworkEvent.serverListening());
            while (true) {
                d(TAG, "Waiting for channels to become available");
                int channelsReady = selector.select();
                if (!selector.isOpen()) {
                    break;
                }

                d(TAG, "%s channels ready", channelsReady);
                Iterator<SelectionKey> selectionKeyIterator = selector.selectedKeys().iterator();
                while (selectionKeyIterator.hasNext()) {
                    SelectionKey key = selectionKeyIterator.next();
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
                    selectionKeyIterator.remove();

                }
            }
            subscriber.onComplete();
        } catch (Exception e) {
            subscriber.onError(e);
        } finally {
            IO.closeQuietly(selector);
            IO.closeQuietly(serverSocketChannel);
            this.subscriber = null;
        }

    }

    private void processClientConnection(SelectionKey clientSelectionKey) {
        if (clientSelectionKey.isReadable()) {
            v(TAG, "Reading from client connection");
            SocketChannel clientChannel = (SocketChannel) clientSelectionKey.channel();

            try {
                ByteBuffer bytesRead = connectedClients.get(clientChannel.hashCode()).read();
                subscriber.onNext(NetworkEvent.newData(bytesRead, clientChannel.hashCode()));

            } catch (Exception e) {
                w(TAG, "Error when reading from client", e);
                checkAndDisconnect(clientSelectionKey, clientChannel);
            }
        }
    }

    private void checkAndDisconnect(SelectionKey clientSelectionKey, SocketChannel clientChannel) {
        if (!clientChannel.isConnected() || !clientChannel.isOpen() || clientChannel.socket().isClosed()) {
            disconnectClient(clientSelectionKey, clientChannel);

        }
    }

    private void disconnectClient(SelectionKey clientSelectionKey, SocketChannel clientChannel) {
        connectedClients.remove(clientChannel.hashCode());
        IO.closeQuietly(clientChannel);
        clientSelectionKey.cancel();
        subscriber.onNext(NetworkEvent.clientDisconnected(clientChannel.hashCode()));
    }


    private void initialiseServerSocket() throws IOException {
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
        SelectionKey clientKey;
        try {
            clientSocketChannel = serverSocketChannel.accept();
            subscriber.onNext(NetworkEvent.newClient(clientSocketChannel.hashCode()));
            clientSocketChannel.configureBlocking(false);
            d(TAG, "Registering client channel with selector");
            clientKey = clientSocketChannel.register(selector, SelectionKey.OP_READ);
            clientKey.attach(ChannelType.CLIENT);
            connectedClients.put(clientSocketChannel.hashCode(), new Client(clientKey));
        } catch (IOException e) {
            w(TAG, "Couldnt accept client channel", e);
            IO.closeQuietly(clientSocketChannel);
        }


    }
}