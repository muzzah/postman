package com.siia.postman.server.nio;


import android.util.Log;

import com.siia.commons.core.io.IO;
import com.siia.commons.core.log.Logcat;
import com.siia.postman.server.PostmanClient;
import com.siia.postman.server.PostmanClientEvent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.UUID;

import io.reactivex.Completable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

public class NIOPostmanClient implements PostmanClient {
    private static final String TAG = Logcat.getTag();

    private final MessageQueueLoop messageRouter;
    private PublishSubject<PostmanClientEvent> clientEventStream;
    private ServerClient client;

    public NIOPostmanClient() {
        messageRouter = new MessageQueueLoop();
    }

    @Override
    public PublishSubject<PostmanClientEvent> getClientEventStream() {
        clientEventStream = PublishSubject.create();
        return clientEventStream;
    }

    @Override
    public void connect(String host, int port) {
        messageRouter.messageRouterEventStream()
                .observeOn(Schedulers.computation())
                .map(
                        event -> {
                            switch (event.type()) {
                                case CLIENT_REGISTERED:
                                    return PostmanClientEvent.clientConnected();
                                case CLIENT_UNREGISTERED:
                                    return PostmanClientEvent.clientDisconnected();
                                default:
                                    Logcat.w(TAG, "Unhandled event from messageRouter in postman client");
                                    return PostmanClientEvent.clientDisconnected();

                            }
                        })
                .subscribe(
                        clientEvent -> {
                            switch (clientEvent.type()) {
                                case CONNECTED:
                                    Log.d(TAG, "Postman client connected");
                                    clientEventStream.onNext(clientEvent);
                                    break;
                                case DISCONNECTED:
                                    Log.d(TAG, "Postman client disconnected");
                                    clientEventStream.onNext(clientEvent);
                                    messageRouter.shutdown();
                                    break;
                                default:
                                    Logcat.w(TAG, "Unhandled event in client message router loop");
                            }


                        },
                        error -> {
                            Logcat.e(TAG, "Client Message router loop ended unexpectedly", error);
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
                client = new ServerClient(UUID.randomUUID(), socketChannel);
                messageRouter.addClient(client);

                subscriber.onComplete();
            } catch (IOException exception) {
                Log.d(TAG, "Problem when connecting to server", exception);
                messageRouter.shutdown();
                subscriber.onError(exception);
            }


        }).subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .subscribe(() -> Logcat.d(TAG, "Client Event setup finished"),
                        error ->
                        {
                            Logcat.e(TAG, "Error received %s", error);
                            clientEventStream.onError(error);
                        });
    }

    @Override
    public void disconnect() {
        messageRouter.shutdown();
    }

    @Override
    public boolean isConnected() {
        return client != null && client.isValid() && messageRouter.isRunning();
    }
}
