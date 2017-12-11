package com.siia.postman.server.nio;


import android.util.Log;

import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.ClientAuthenticator;
import com.siia.postman.server.PostmanClient;
import com.siia.postman.server.PostmanClientEvent;
import com.siia.postman.server.PostmanMessage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.UUID;

import javax.inject.Provider;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

public class NIOPostmanClient implements PostmanClient {
    private static final String TAG = Logcat.getTag();

    private MessageQueueLoop messageRouter;
    private PublishSubject<PostmanClientEvent> clientEventStream;
    private NIOConnection client;
    private final Scheduler computation;
    private final Provider<PostmanMessage> messageProvider;
    private ClientAuthenticator clientAuthenticator;


    public NIOPostmanClient(Scheduler computation, Provider<PostmanMessage> messageProvider) {
        this.computation = computation;
        this.messageProvider = messageProvider;
    }

    @Override
    public PublishSubject<PostmanClientEvent> getClientEventStream() {
        clientEventStream = PublishSubject.create();
        return clientEventStream;
    }

    @Override
    public void connect(String host, int port) {
        messageRouter = new MessageQueueLoop();
        Observable<PostmanClientEvent> mappedItems = messageRouter.messageRouterEventStream()
                .observeOn(computation)
                .map(
                        event -> {
                            switch (event.type()) {
                                case CLIENT_REGISTERED:
                                    return PostmanClientEvent.clientConnected();
                                case CLIENT_UNREGISTERED:
                                    messageRouter.shutdown();
                                    return PostmanClientEvent.clientDisconnected();
                                case MESSAGE:
                                    return PostmanClientEvent.newMessage(event.client(), event.msg());
                                case CLIENT_REGISTRATION_FAILED:
                                    messageRouter.shutdown();
                                    return PostmanClientEvent.clientDisconnected();
                                default:
                                    Logcat.w(TAG, "Unhandled event from messageRouter in postman connection [%s]", event.toString());
                                    return PostmanClientEvent.ignoreEvent();

                            }
                        });
        mappedItems
                .subscribe(
                        clientEvent -> {
                            switch (clientEvent.type()) {
                                case CONNECTED:
                                    Log.d(TAG, "Postman connection connected");
                                    break;
                                case DISCONNECTED:
                                    Log.d(TAG, "Postman connection disconnected");
                                    clientEventStream.onNext(clientEvent);
                                    break;
                                case NEW_MESSAGE:
                                    if (clientAuthenticator == null) {
                                        clientAuthenticator = new ClientAuthenticator(this);
                                        clientAuthenticator.beginAuthentication(mappedItems, clientEvent.msg());
                                    } else if (clientAuthenticator.isAuthenticated()) {
                                        Logcat.v(TAG, "Forwarding message, authenticated");
                                        clientEventStream.onNext(clientEvent);
                                    }
                                    break;
                                default:
                                    Logcat.w(TAG, "Unhandled event from messageRouter in postman connection [%s]", clientEvent.toString());
                            }


                        },
                        error -> {
                            Logcat.e(TAG, "Client Message router loop ended unexpectedly", error);
                            disconnect();
                            clientEventStream.onError(error);
                        },
                        () ->

                        {
                            Logcat.i(TAG, "Client Message loop completed");
                            clientEventStream.onComplete();
                        });


        Completable.create(subscriber ->

        {


            try {
                SocketChannel socketChannel = SocketChannel.open();
                messageRouter.startMessageQueueLoop();

                InetSocketAddress serverAdress = new InetSocketAddress(host, port);
                socketChannel.connect(serverAdress);
                socketChannel.configureBlocking(false);
                client = new NIOConnection(UUID.randomUUID(), socketChannel, messageProvider);
                messageRouter.addClient(client);
                subscriber.onComplete();
            } catch (IOException exception) {
                Log.d(TAG, "Problem when connecting to server", exception);
                messageRouter.shutdown();
                subscriber.onError(exception);
            }


        }).subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .subscribe(() -> Logcat.v(TAG, "Connected"),
                        error ->
                        {
                            Logcat.e(TAG, "Error received %s", error);
                            clientEventStream.onError(error);
                        });
    }

    @Override
    public void sendMessage(PostmanMessage msg) {
        messageRouter.addMessageToQueue(msg, client);
    }

    @Override
    public void disconnect() {
        messageRouter.shutdown();
        client.destroy();
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
