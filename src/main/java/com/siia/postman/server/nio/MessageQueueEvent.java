package com.siia.postman.server.nio;

import android.support.annotation.NonNull;

import com.siia.postman.server.PostmanMessage;

import java.util.HashMap;
import java.util.Map;

class MessageQueueEvent {


    public static MessageQueueEvent ready() {
        return new MessageQueueEvent(null, Type.READY);
    }

    enum Type {
        CLIENT_REGISTERED,
        CLIENT_REGISTRATION_FAILED,
        CLIENT_UNREGISTERED,
        READY, MESSAGE
    }

    private NIOConnection client;

    private Type type;
    private Map<Type, Object> attributes;

    private MessageQueueEvent(NIOConnection client, Type type) {
        this.client = client;
        this.type = type;
        attributes = new HashMap<>();
    }

    Type type() {
        return type;
    }

    NIOConnection client() {return client; }

    MessageQueueEvent addAttribute(Type key, @NonNull PostmanMessage msg) {
        attributes.put(key, msg);
        return this;
    }

    PostmanMessage msg() {
        return (PostmanMessage) attributes.get(Type.MESSAGE);
    }

    static MessageQueueEvent clientRegistered(@NonNull NIOConnection client) {
        return new MessageQueueEvent(client, Type.CLIENT_REGISTERED);
    }

    static MessageQueueEvent clientUnregistered(@NonNull NIOConnection client) {
        return new MessageQueueEvent(client, Type.CLIENT_UNREGISTERED);
    }

    static MessageQueueEvent clientRegistrationFailed(@NonNull NIOConnection client) {
        return new MessageQueueEvent(client, Type.CLIENT_REGISTRATION_FAILED);
    }

    static MessageQueueEvent messageReceived(@NonNull NIOConnection client, @NonNull PostmanMessage msg) {
        return new MessageQueueEvent(client, Type.MESSAGE).addAttribute(Type.MESSAGE, msg);
    }

    @Override
    public String toString() {
        return "MessageQueueEvent{" +
                "connection=" + client +
                ", type=" + type +
                ", attributes=" + attributes +
                '}';
    }
}
