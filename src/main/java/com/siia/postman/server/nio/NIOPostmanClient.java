package com.siia.postman.server.nio;


import android.annotation.SuppressLint;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.protobuf.MessageLite;
import com.siia.commons.core.io.IO;
import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.Connection;
import com.siia.postman.server.PostmanClient;
import com.siia.postman.server.PostmanClientEvent;
import com.siia.postman.server.PostmanMessage;

import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

import javax.inject.Provider;

import io.reactivex.Scheduler;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;

@SuppressLint("MissingPermission")
public class NIOPostmanClient implements PostmanClient {
    private static final String TAG = Logcat.getTag();

    private MessageQueueLoop messageRouter;
    private FlowableProcessor<PostmanClientEvent> clientEventStream;
    private NIOConnection client;
    private final Scheduler computation;
    private final Provider<PostmanMessage> messageProvider;
    private final CompositeDisposable disposables;
    private final Scheduler ioScheduler;
    private final Scheduler newThreadScheduler;


    public NIOPostmanClient(Scheduler computation, Provider<PostmanMessage> messageProvider, Scheduler ioScheduler, Scheduler newThreadScheduler) {
        this.computation = computation;
        this.messageProvider = messageProvider;
        this.ioScheduler = ioScheduler;
        this.newThreadScheduler = newThreadScheduler;
        this.disposables = new CompositeDisposable();
        this.clientEventStream = PublishProcessor.<PostmanClientEvent>create().toSerialized();
    }

    @Override
    public FlowableProcessor<PostmanClientEvent> getClientEventStream() {
        return clientEventStream;
    }

    @Override
    public void connect(@NonNull final SocketChannel socketChannel, @NonNull InetAddress host, int port) {
        if (isConnected()) {
            Logcat.w(TAG, "Already connected");
            return;
        }

        messageRouter = new MessageQueueLoop(newThreadScheduler);
        Disposable msgQueueDisposable = messageRouter.messageQueueEventsStream()
                .observeOn(computation)
                .map(
                        event -> {
                            switch (event.type()) {
                                case CLIENT_REGISTERED:
                                    return PostmanClientEvent.clientConnected();
                                case CLIENT_UNREGISTERED:
                                    return PostmanClientEvent.clientDisconnected();
                                case MESSAGE:
                                    return PostmanClientEvent.newMessage(event.client(), event.msg());
                                case CLIENT_REGISTRATION_FAILED:
                                    return PostmanClientEvent.clientDisconnected();
                                default:
                                    return PostmanClientEvent.ignoreEvent();
                            }
                        })
                .subscribe(
                        clientEvent -> {
                            switch (clientEvent.type()) {
                                case CONNECTED:
                                    Log.v(TAG, "Connected");
                                    clientEventStream.onNext(clientEvent);
                                    break;
                                case DISCONNECTED:
                                    Log.v(TAG, "Disconnected");
                                    disconnect();
                                    clientEventStream.onNext(clientEvent);
                                    break;
                                case NEW_MESSAGE:
                                    Log.v(TAG, "Next Msg");
                                    clientEventStream.onNext(clientEvent);
                                    break;
                                case IGNORE:
                                    break;
                                default:
                                    Logcat.w(TAG, "Unhandled event from messageRouter [%s]", clientEvent.type());
                            }
                        },
                        error -> {
                            disconnect();
                            clientEventStream.onError(error);
                        },
                        () -> {
                            disconnect();
                            clientEventStream.onComplete();
                        });


        Disposable socketConnectionDisposable = messageRouter.startMessageQueueLoop()
                .observeOn(ioScheduler)
                .subscribe(
                        readyMsg -> connectToServer(socketChannel, host, port),
                        error -> {
                            disconnect();
                            clientEventStream.onError(error);
                        },
                        () -> Logcat.i(TAG, "Message Queue completed"));


        disposables.add(socketConnectionDisposable);

        disposables.add(msgQueueDisposable);

    }

    private void connectToServer(@NonNull SocketChannel socketChannel, @NonNull InetAddress host, int port) {
        try {
            socketChannel.socket().setKeepAlive(true);
            socketChannel.socket().setPerformancePreferences(Connection.CONNECTION_TIME_PREFERENCE,
                    Connection.LATENCY_PREFERENCE, Connection.BANDWIDTH_PREFERENCE);
            if (!socketChannel.connect(new InetSocketAddress(host, port))) {
                Logcat.d(TAG, "connect return false, still connecting possibly");
            }

            socketChannel.configureBlocking(false);
        } catch (Throwable e) {
            Logcat.e(TAG, "Problem connecting to teacher", e);
            handleFailureToConnect(socketChannel);
            return;
        }
        client = new NIOConnection(socketChannel, messageProvider);
        messageRouter.addClient(client);
    }

    private void handleFailureToConnect(@NonNull SocketChannel socketChannel) {
        IO.closeQuietly(socketChannel);
        clientEventStream.onError(new ConnectException("Could not establish connection "));
        disconnect();
    }

    @Override
    public void sendMessage(@NonNull PostmanMessage msg) {
        messageRouter.addMessageToQueue(msg, client);
    }

    @Override
    public void sendMessage(@NonNull MessageLite msg) {
        messageRouter.addMessageToQueue(new PostmanMessage(msg), client);
    }

    @Override
    public void disconnect() {
        disposables.clear();

        if (messageRouter != null) {
            messageRouter.shutdown();
        }

        if (client != null) {
            client.destroy();
        }

    }

    @Override
    public boolean isConnected() {
        return client != null && client.isValid() && messageRouter != null && messageRouter.isRunning();
    }

}
