package com.siia.postman.server.nio;


import android.support.annotation.NonNull;

import com.google.protobuf.MessageLite;
import com.siia.commons.core.io.IO;
import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.PostmanClient;
import com.siia.postman.server.PostmanClientEvent;
import com.siia.postman.server.PostmanMessage;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.Scheduler;

import static com.siia.commons.core.log.Logcat.v;
import static java.util.Objects.nonNull;

public class NIOPostmanClient implements PostmanClient {
    private static final String TAG = Logcat.getTag();

    private NIOConnection client;
    private final Scheduler newThreadScheduler;
    private final SelectorProvider selectorProvider;
    private final NIOConnectionFactory nioConnectionFactory;
    private AbstractSelector selector;


    NIOPostmanClient(Scheduler newThreadScheduler,
                     SelectorProvider selectorProvider, NIOConnectionFactory nioConnectionFactory) {
        this.newThreadScheduler = newThreadScheduler;
        this.selectorProvider = selectorProvider;
        this.nioConnectionFactory = nioConnectionFactory;
    }

    @Override
    public Flowable<PostmanClientEvent> connect(@NonNull final SocketChannel socketChannel, @NonNull InetAddress host, int port) {
        if (isConnected()) {
            return Flowable.error(new IllegalStateException("Already connected"));
        }

        return Flowable.<PostmanClientEvent>create(emitter -> {

            try {
                selector = selectorProvider.openSelector();
                client = nioConnectionFactory.connectToServer(selector, socketChannel, host, port);
            } catch (Throwable e) {
                Logcat.e(TAG, "Problem connecting to server", e);
                disconnect();
                emitter.onError(e);
                return;
            }

            emitter.onNext(PostmanClientEvent.clientConnected());

            try {
                while (true) {
                    v(TAG, "Waiting for selector updates");
                    int channelsReady = selector.select();

                    if (!selector.isOpen()) {
                        break;
                    }

                    if (selector.selectedKeys().isEmpty()) {
                        Logcat.w(TAG, "Selected keys are empty");
                        continue;
                    }

                    v(TAG, "%s channel(s) ready in accept loop", channelsReady);

                    processKeyUpdates(emitter);

                }
                emitter.onComplete();
            } catch (Throwable e) {
                emitter.onError(e);
            } finally {
                disconnect();
            }


        }, BackpressureStrategy.BUFFER)
                .subscribeOn(newThreadScheduler);

    }

    private void processKeyUpdates(FlowableEmitter<PostmanClientEvent> emitter) throws IOException {
        for (SelectionKey selectionKey : selector.selectedKeys()) {
            v(TAG, "SK : valid=%b read=%b write=%b accept=%b", selectionKey.isValid(), selectionKey.isReadable(),
                    selectionKey.isWritable(), selectionKey.isAcceptable());

            if (!selectionKey.isValid()) {
                throw new IllegalStateException("Selection key has been invalidated");
            }


            if (selectionKey.isValid() && selectionKey.isReadable()) {
                client.read();

                client.filledMessages().forEach(msg -> {
                    Logcat.v(TAG, "Message received [%s]", msg.toString());
                    emitter.onNext(PostmanClientEvent.newMessage(msg));
                });
            }

            if (selectionKey.isValid() && selectionKey.isWritable()) {
                client.sendMessages();
            }
        }

        selector.selectedKeys().clear();
    }

    @Override
    public void sendMessage(@NonNull PostmanMessage msg) {
        client.addMessageToSend(msg);
        selector.wakeup();
    }

    @Override
    public void sendMessage(@NonNull MessageLite msg) {
        sendMessage(new PostmanMessage(msg));
    }

    @Override
    public void disconnect() {

        if (nonNull(client)) {
            client.disconnect();
        }

        IO.closeQuietly(selector);
    }

    @Override
    public boolean isConnected() {
        return nonNull(client) && client.isConnected();

    }

}
