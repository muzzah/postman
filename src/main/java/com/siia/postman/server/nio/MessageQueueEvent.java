package com.siia.postman.server.nio;

import android.support.annotation.NonNull;

import com.siia.postman.server.PostmanMessage;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.nonNull;

class MessageQueueEvent {


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

    private MessageQueueEvent addAttribute(Type key, @NonNull PostmanMessage msg) {
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



    static MessageQueueEvent ready() {
        return new MessageQueueEvent(null, Type.READY);
    }

    @Override
    public String toString() {
        return "MessageQueueEvent{" +
                "connection=" + client +
                ", type=" + type +
                ", attributes=" + attributes +
                '}';
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(type)
                .append(attributes)
                .append(client)
        .toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return nonNull(obj) && obj instanceof MessageQueueEvent &&
                new EqualsBuilder().
                        append(type, ((MessageQueueEvent) obj).type)
                .append(attributes, ((MessageQueueEvent) obj).attributes)
                .append(client, ((MessageQueueEvent) obj).client)
                .isEquals();
    }
}
