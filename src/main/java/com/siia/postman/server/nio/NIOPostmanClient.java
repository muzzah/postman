package com.siia.postman.server.nio;


import android.util.Log;

import com.google.protobuf.MessageLite;
import com.siia.commons.core.io.IO;
import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.PostmanClient;
import com.siia.postman.server.PostmanClientEvent;
import com.siia.postman.server.PostmanMessage;

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

import javax.inject.Provider;

import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.PublishSubject;

import static com.siia.commons.core.check.Check.checkState;

public class NIOPostmanClient implements PostmanClient {
    private static final String TAG = Logcat.getTag();

    private MessageQueueLoop messageRouter;
    private final PublishSubject<PostmanClientEvent> clientEventStream;
    private NIOConnection client;
    private final Scheduler computation;
    private final Provider<PostmanMessage> messageProvider;
    private final CompositeDisposable disposables;
    private final Scheduler ioScheduler;


    public NIOPostmanClient(Scheduler computation, Provider<PostmanMessage> messageProvider, Scheduler ioScheduler) {
        this.computation = computation;
        this.messageProvider = messageProvider;
        this.ioScheduler = ioScheduler;
        this.disposables = new CompositeDisposable();
        this.clientEventStream = PublishSubject.create();

    }

    @Override
    public PublishSubject<PostmanClientEvent> getClientEventStream() {
        return clientEventStream;
    }

    @Override
    public void connect(String host, int port) {
        checkState(!isConnected(), "Already connected");
        messageRouter = new MessageQueueLoop();
        Observable<PostmanClientEvent> mappedItems = messageRouter.messageRouterEventStream()
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
                        });

        Disposable clientEventsDisposable = mappedItems
                .subscribe(
                        clientEvent -> {
                            switch (clientEvent.type()) {
                                case CONNECTED:
                                    Log.d(TAG, "Connected");
                                    clientEventStream.onNext(clientEvent);
                                    break;
                                case DISCONNECTED:
                                    Log.d(TAG, "Disconnected");
                                    disconnect();
                                    clientEventStream.onNext(clientEvent);
                                    break;
                                case NEW_MESSAGE:
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


        Disposable socketConnectionDisposable = messageRouter.messageRouterEventStream()
                .observeOn(ioScheduler)
                .filter(messageQueueEvent -> messageQueueEvent.type().equals(MessageQueueEvent.Type.READY))
                .doOnSubscribe(disposable -> messageRouter.startMessageQueueLoop())
                .subscribe(
                        messageQueueEvent -> {
                            SocketChannel socketChannel = null;
                            try {
                                InetSocketAddress serverAddress = new InetSocketAddress(host, port);
                                socketChannel = SocketChannel.open();
                                socketChannel.connect(serverAddress);
                                socketChannel.configureBlocking(false);
                            } catch (Exception e) {
                                IO.closeQuietly(socketChannel);
                                clientEventStream.onError(e);
                                //Important to do this last, otherwise disposable are cleared and
                                //an undeliverable exception is thrown
                                disconnect();
                                return;
                            }
                            client = new NIOConnection(socketChannel, messageProvider);
                            messageRouter.addClient(client);
                        },
                        error -> {
                            //Leave error handling to above subscriber
                        });

        disposables.add(socketConnectionDisposable);

        disposables.add(clientEventsDisposable);

    }

    @Override
    public void sendMessage(PostmanMessage msg) {
        messageRouter.addMessageToQueue(msg, client);
    }

    @Override
    public void sendMessage(MessageLite msg) {
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
