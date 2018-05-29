package org.postman.server.nio;

import android.support.annotation.NonNull;

import net.jodah.concurrentunit.Waiter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.postman.server.Connection;
import org.postman.server.PostmanClientEvent;
import org.postman.server.PostmanMessage;
import org.postman.server.PostmanServerEvent;
import org.postman.server.nio.Test.Ping;
import org.postman.server.nio.Test.Pong;
import org.reactivestreams.Subscription;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import io.reactivex.FlowableSubscriber;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

import static java.util.Objects.isNull;
import static junit.framework.TestCase.fail;

/**
 * Copyright Siia 2018
 */
public class ServerIntegrationTest {
    private static final Consumer<PostmanClientEvent> DEFAULT_CLIENT_CONSUMER = postmanServerEvent -> {
        switch (postmanServerEvent.type()) {
            case CONNECTED:
                System.out.println("Connected");
                break;
            case NEW_MESSAGE:
                System.out.println("New Msg");
                break;
        }
    };
    private static final String HOST = "127.0.0.1";

    private NIOPostmanServer postmanServer;
    private SelectorProvider provider = SelectorProvider.provider();
    private List<NIOPostmanClient> clientsToDisconnect;
    private boolean failTest;

    @Before
    public void setup() {
        clientsToDisconnect = new ArrayList<>();
        postmanServer = new NIOPostmanServer(new ServerEventLoop(provider,
                new NIOConnectionFactory(PostmanMessage::new),
                Schedulers.newThread()));
        failTest = false;
    }

    @After
    public void tearDown() {
        clientsToDisconnect.forEach(NIOPostmanClient::disconnect);
        postmanServer.stopServer();
    }

    @Test
    public void serverShouldHandleMultipleClientsConnecting() throws IOException, TimeoutException {
        Waiter waiter = new Waiter();
        startServer(event -> {
            switch (event.type()) {
                case SERVER_LISTENING:
                case CLIENT_JOIN:
                    waiter.resume();
            }
        });
        waiter.await();

        connectClient(createPostmanClient(), DEFAULT_CLIENT_CONSUMER);
        connectClient(createPostmanClient(), DEFAULT_CLIENT_CONSUMER);
        connectClient(createPostmanClient(), DEFAULT_CLIENT_CONSUMER);
        waiter.await(3000, 3);
        checkFailSignal();

    }

    private void checkFailSignal() {
        if (failTest) {
            fail("Fail test signal true, error received");
        }
    }

    @Test
    public void serverShouldDetectClientDisconnections() throws IOException, TimeoutException {
        Waiter waiter = new Waiter();
        startServer(event -> {
            switch (event.type()) {
                case SERVER_LISTENING:
                case CLIENT_DISCONNECT:
                    waiter.resume();
            }
        });
        waiter.await();

        disconnectingClient();
        disconnectingClient();
        disconnectingClient();
        waiter.await(3000, 3);
        checkFailSignal();

    }

    @Test
    public void serverShouldBroadcastMessagesToAllClients() throws IOException, TimeoutException {
        Waiter waiter = new Waiter();
        Collection<String> responses = new ArrayList<>();
        startServer(event -> {
            switch (event.type()) {
                case CLIENT_JOIN:
                case SERVER_LISTENING:
                    waiter.resume();
                    break;
                case NEW_MESSAGE:
                    String response = event.message().<Pong>getProtoObj().getMsg();
                    if (responses.contains(response)) {
                        throw new IllegalStateException("Received duplicate response");
                    }

                    responses.add(response);
                    waiter.resume();
                    break;
            }
        });
        waiter.await();
        respondingClient();
        respondingClient();
        respondingClient();
        waiter.await(3000, 3);
        postmanServer.broadcastMessage(Ping.getDefaultInstance());
        waiter.await(3000, 3);
        checkFailSignal();

    }

    @Test
    public void serverShouldSendMessageToOneClient() throws IOException, TimeoutException {
        Waiter waiter = new Waiter();
        startServer(new Consumer<PostmanServerEvent>() {
            private Connection connectionToSendTo;

            @Override
            public void accept(PostmanServerEvent postmanServerEvent) {
                switch (postmanServerEvent.type()) {
                    case CLIENT_JOIN:
                        if (isNull(connectionToSendTo)) {
                            connectionToSendTo = postmanServerEvent.connection();
                            postmanServer.sendMessage(Ping.getDefaultInstance(), connectionToSendTo);
                        }
                    case SERVER_LISTENING:
                        waiter.resume();
                        break;
                    case NEW_MESSAGE:
                        if (postmanServerEvent.connection().equals(connectionToSendTo)) {
                            waiter.resume();
                        }
                        break;
                }
            }
        });
        waiter.await();
        respondingClient();
        respondingClient();
        respondingClient();
        waiter.await(3000, 4);
        checkFailSignal();

    }


    @Test
    public void stressPingPongTest() throws IOException, TimeoutException {
        Waiter waiter = new Waiter();
        startServer(new Consumer<PostmanServerEvent>() {
            Map<Connection, Integer> counts = new HashMap<>();

            @Override
            public void accept(PostmanServerEvent postmanServerEvent) {
                switch (postmanServerEvent.type()) {
                    case SERVER_LISTENING:
                        waiter.resume();
                        break;
                    case NEW_MESSAGE:

                        Integer count = counts.getOrDefault(postmanServerEvent.connection(), 0);

                        if (count < 1000) {
                            count++;
                            counts.put(postmanServerEvent.connection(), count);
                            postmanServerEvent.connection().queueMessageToSend(new PostmanMessage(Pong.newBuilder().setMsg("pong").build()));
                        }

                        int sum = counts.values().stream().reduce((integer, integer2) -> integer + integer2).get();

                        if (sum == 3000) {
                            waiter.resume();
                            return;
                        }

                        break;
                }
            }
        });
        waiter.await();
        pingingClients(waiter);
        pingingClients(waiter);
        pingingClients(waiter);
        waiter.await(10000, 4);
        checkFailSignal();

    }

    private void pingingClients(Waiter waiter) throws IOException {
        NIOPostmanClient postmanClient = createPostmanClient();
        connectClient(postmanClient, new Consumer<PostmanClientEvent>() {
            int count = 0;

            @Override
            public void accept(PostmanClientEvent postmanClientEvent) {
                switch (postmanClientEvent.type()) {
                    case CONNECTED:
                        count++;
                        postmanClient.sendMessage(Ping.getDefaultInstance());
                        break;
                    case NEW_MESSAGE:
                        if (count == 1000) {
                            waiter.resume();
                            return;
                        } else if (count < 1000) {
                            count++;
                            postmanClient.sendMessage(Ping.getDefaultInstance());
                        }
                        break;
                }
            }
        });
    }

    private void respondingClient() throws IOException {
        NIOPostmanClient postmanClient3 = createPostmanClient();
        connectClient(postmanClient3, event -> {
            switch (event.type()) {
                case NEW_MESSAGE:
                    postmanClient3.sendMessage(Pong.newBuilder().setMsg(UUID.randomUUID().toString()).build());
                    break;
            }
        });
    }

    private void startServer(Consumer<PostmanServerEvent> eventHandler) {
        postmanServer.serverStart(new InetSocketAddress(HOST, 12345))
                .observeOn(Schedulers.computation())
                .subscribe(postmanServerEvent -> {
                    System.out.println("Server : " + postmanServerEvent.type().name());
                    eventHandler.accept(postmanServerEvent);
                }, error -> {
                    failTest = true;
                    error.printStackTrace();
                }, () -> System.out.println("Server Completed"));


    }

    @NonNull
    private NIOPostmanClient createPostmanClient() {
        NIOPostmanClient postmanClient = new NIOPostmanClient(Schedulers.newThread(), provider, new NIOConnectionFactory(PostmanMessage::new));
        clientsToDisconnect.add(postmanClient);
        return postmanClient;
    }

    private void connectClient(NIOPostmanClient postmanClient, Consumer<PostmanClientEvent> eventHandler) throws IOException {

        postmanClient.connect(provider.openSocketChannel(), InetAddress.getByName(HOST), 12345)
                .observeOn(Schedulers.computation())
                .subscribe(eventHandler,
                        error -> {
                            failTest = true;
                            error.printStackTrace();
                        }, () -> System.out.println("Client Completed"));
    }

    private void disconnectingClient() throws IOException {

        NIOPostmanClient nioPostmanClient = createPostmanClient();
        nioPostmanClient
                .connect(provider.openSocketChannel(), InetAddress.getByName(HOST), 12345)
                .observeOn(Schedulers.computation())
                .subscribe(new FlowableSubscriber<PostmanClientEvent>() {
                    private Subscription subscription;

                    @Override
                    public void onSubscribe(Subscription subscription) {
                        this.subscription = subscription;
                        subscription.request(Integer.MAX_VALUE);
                    }

                    @Override
                    public void onNext(PostmanClientEvent postmanClientEvent) {
                        if (postmanClientEvent.type() == PostmanClientEvent.Type.CONNECTED) {
                            nioPostmanClient.disconnect();
                            subscription.cancel();
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        failTest = true;
                        t.printStackTrace();
                    }

                    @Override
                    public void onComplete() {
                        System.out.println("Client Completed");
                    }
                });
    }

}
