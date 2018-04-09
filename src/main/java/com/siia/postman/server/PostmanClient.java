package com.siia.postman.server;


import android.support.annotation.NonNull;

import com.google.protobuf.MessageLite;

import java.nio.channels.SocketChannel;

import javax.inject.Singleton;

import io.reactivex.subjects.PublishSubject;

@Singleton
public interface PostmanClient {

    PublishSubject<PostmanClientEvent> getClientEventStream();

    void connect(@NonNull SocketChannel socketChannel, @NonNull String host, int port);

    /**
     * Add message top be sent. Messages are not guaranteed to be sent once this method returns.
     * The message will likely be sent at some point after this method returns
     * @param msg the message to send
     */
    void sendMessage(@NonNull PostmanMessage msg);
    void sendMessage(@NonNull MessageLite msg);

    void disconnect();

    boolean isConnected();

}
