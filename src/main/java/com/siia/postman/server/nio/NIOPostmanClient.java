package com.siia.postman.server.nio;


import android.util.Log;

import com.siia.commons.core.io.IO;
import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.ClientAuthenticator;
import com.siia.postman.server.PostmanClient;
import com.siia.postman.server.PostmanClientEvent;
import com.siia.postman.server.PostmanMessage;

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.UUID;

import javax.inject.Provider;

import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static com.siia.commons.core.check.Check.checkState;

public class NIOPostmanClient implements PostmanClient {
    private static final String TAG = Logcat.getTag();

    private MessageQueueLoop messageRouter;
    private PublishSubject<PostmanClientEvent> clientEventStream;
    private NIOConnection client;
    private final Scheduler computation;
    private final Provider<PostmanMessage> messageProvider;
    private ClientAuthenticator clientAuthenticator;
    private final CompositeDisposable disposables;


    public NIOPostmanClient( Scheduler computation, Provider<PostmanMessage> messageProvider) {
        this.computation = computation;
        this.messageProvider = messageProvider;
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
        disposables.add(mappedItems
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
                                    if (clientAuthenticator == null) {
                                        clientAuthenticator = new ClientAuthenticator(this);
                                        clientAuthenticator.beginAuthentication(mappedItems, clientEvent.msg());
                                    } else if (clientAuthenticator.isAuthenticated()) {
                                        clientEventStream.onNext(clientEvent);
                                    }
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
                        }));


        disposables.add(messageRouter.messageRouterEventStream().observeOn(Schedulers.io())
                .filter(messageQueueEvent -> messageQueueEvent.type().equals(MessageQueueEvent.Type.READY))
                .doOnSubscribe(disposable -> messageRouter.startMessageQueueLoop())
                .subscribe(
                        messageQueueEvent -> {
                            SocketChannel socketChannel = null;
                            try {
                                InetSocketAddress serverAdress = new InetSocketAddress(host, port);
                                socketChannel = SocketChannel.open();
                                socketChannel.connect(serverAdress);
                                socketChannel.configureBlocking(false);
                            }catch (Exception e) {
                                IO.closeQuietly(socketChannel);
                                disconnect();
                                clientEventStream.onError(e);
                                return;
                            }
                            client = new NIOConnection(UUID.randomUUID(), socketChannel, messageProvider);
                            messageRouter.addClient(client);
                        },
                        error -> {
                            //Leave error handling to above subscriber
                        }));

    }

    @Override
    public void sendMessage(PostmanMessage msg) {
        messageRouter.addMessageToQueue(msg, client);
    }

    @Override
    public void disconnect() {
        disposables.clear();

        if(messageRouter != null) {
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

    @Override
    public UUID getClientId() {
        return client.getConnectionId();
    }
}
