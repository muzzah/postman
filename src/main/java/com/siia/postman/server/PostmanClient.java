package com.siia.postman.server;


import com.google.protobuf.MessageLite;

import javax.inject.Singleton;

import io.reactivex.subjects.PublishSubject;

@Singleton
public interface PostmanClient {

    PublishSubject<PostmanClientEvent> getClientEventStream();

    void connect(String host, int port);

    /**
     * Add message top be sent. Messages are not guaranteed to be sent once this method returns.
     * The message will likely be sent at some point after this method returns
     * @param msg the message to send
     */
    void sendMessage(PostmanMessage msg);
    void sendMessage(MessageLite msg);

    void disconnect();

    boolean isConnected();

}
