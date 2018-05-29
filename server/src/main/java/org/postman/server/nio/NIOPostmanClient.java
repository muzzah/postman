package org.postman.server.nio;


import android.support.annotation.NonNull;

import com.google.protobuf.MessageLite;
import com.siia.commons.core.io.IO;
import com.siia.commons.core.log.Logcat;

import org.postman.server.PostmanClient;
import org.postman.server.PostmanClientEvent;
import org.postman.server.PostmanMessage;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.Scheduler;

import static com.siia.commons.core.log.Logcat.v;
import static java.util.Objects.nonNull;

public class NIOPostmanClient implements PostmanClient {
    private static final String TAG = Logcat.getTag();

    private NIOConnection client;
    private AbstractSelector selector;
    private final Scheduler newThreadScheduler;
    private final SelectorProvider selectorProvider;
    private final NIOConnectionFactory nioConnectionFactory;
    private final AtomicBoolean shouldLoop;



    NIOPostmanClient(Scheduler newThreadScheduler,
                     SelectorProvider selectorProvider, NIOConnectionFactory nioConnectionFactory) {
        this.newThreadScheduler = newThreadScheduler;
        this.selectorProvider = selectorProvider;
        this.nioConnectionFactory = nioConnectionFactory;
        this.shouldLoop = new AtomicBoolean(false);
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

            emitter.onNext(PostmanClientEvent.isConnectedEvent());

            try {
                shouldLoop.set(true);
                while (shouldLoop.get()) {
                    v(TAG, "Waiting for selector updates");
                    int channelsReady = selector.select();

                    if (!selector.isOpen() || !client.isConnected() || !shouldLoop.get()) {
                        //Likely we have shutdown, exit loop
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
                emitter.tryOnError(e);
            } finally {
                disconnect();
            }


        }, BackpressureStrategy.BUFFER)
                .subscribeOn(newThreadScheduler);

    }

    private void processKeyUpdates(FlowableEmitter<PostmanClientEvent> emitter) throws IOException {
        for (SelectionKey selectionKey : selector.selectedKeys()) {

            if (!selectionKey.isValid()) {
                throw new IllegalStateException("Selection key has been invalidated");
            }

            v(TAG, "SK : valid=%b read=%b write=%b accept=%b", selectionKey.isValid(), selectionKey.isReadable(),
                    selectionKey.isWritable(), selectionKey.isAcceptable());

            if (selectionKey.isValid() && selectionKey.isReadable()) {
                client.read();

                client.filledMessages().forEach(msg -> {
                    Logcat.v(TAG, "Message received [%s]", msg.toString());
                    emitter.onNext(PostmanClientEvent.newMessage(msg));
                });
            }

            if (selectionKey.isValid() && selectionKey.isWritable()) {
                client.sendAnyPendingMessages();
            }
        }

        selector.selectedKeys().clear();
    }

    @Override
    public void sendMessage(@NonNull PostmanMessage msg) {
        client.queueMessageToSend(msg);
        selector.wakeup();
    }

    @Override
    public void sendMessage(@NonNull MessageLite msg) {
        sendMessage(new PostmanMessage(msg));
    }

    @Override
    public void disconnect() {
        shouldLoop.set(false);
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
