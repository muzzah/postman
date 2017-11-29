package com.siia.postman.server.ipv4;


import android.util.Log;

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

public class IPPostmanClient implements PostmanClient {
    private static final String TAG = Logcat.getTag();

    private final IPIOMessageRouter messageRouter;
    private PublishSubject<PostmanClientEvent> clientEventStream;
    private IPPostmanServerClient client;
    private SocketChannel socketChannel;
    private Disposable routerDisposable;

    public IPPostmanClient() {
        clientEventStream = PublishSubject.create();
        messageRouter = new IPIOMessageRouter();
    }

    @Override
    public PublishSubject<PostmanClientEvent> getClientEventStream() {
        return clientEventStream;
    }

    @Override
    public void connect(String host, int port) {
        routerDisposable = messageRouter.messageRouterEventStream()
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
                                    break;
                                default:
                                    Logcat.w(TAG, "Unhandled event in client message router loop");
                            }


                        },
                        error -> {
                            Logcat.e(TAG, "Client Message router loop ended unexpectedly", error);
                            clientEventStream.onError(error);
                            routerDisposable.dispose();
                        },
                        () ->

                        {
                            Logcat.i(TAG, "Client Message loop for client ended");
                            clientEventStream.onComplete();
                            routerDisposable.dispose();
                        });


        Completable.create(subscriber ->

        {


            try {
                socketChannel = SocketChannel.open();
                messageRouter.startMessageQueueLoop();

                InetSocketAddress serverAdress = new InetSocketAddress(host, port);
                socketChannel.connect(serverAdress);
                socketChannel.configureBlocking(false);
                client = new IPPostmanServerClient(UUID.randomUUID(), socketChannel);
                messageRouter.addClient(client);

                subscriber.onComplete();
            } catch (IOException exception) {
                Log.d(TAG, "Problem when connecting to server", exception);
                client.destroy();
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

    }
}
