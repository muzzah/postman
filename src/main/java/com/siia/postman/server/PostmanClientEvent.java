package com.siia.postman.server;


import android.support.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class PostmanClientEvent {


    boolean isNewMessage() {
        return Type.NEW_MESSAGE.equals(type);
    }

    public enum Type {
        CONNECTED,
        DISCONNECTED,
        NEW_MESSAGE,
        IGNORE
    }

    public enum Attribute {
        MESSAGE,
        CLIENT
    }

    private Map<Attribute, Object> attributes;

    private Type type;

    private PostmanClientEvent(@NonNull Type type) {
        this.type = type;
        this.attributes = new HashMap<>();
    }

    private PostmanClientEvent addAttribute(@NonNull Attribute attribute, @NonNull Object vale) {
        attributes.put(attribute, vale);
        return this;
    }

    public Type type() {
        return type;
    }

    public PostmanMessage msg() {
        return (PostmanMessage) attributes.get(Attribute.MESSAGE);
    }

    public Connection client() {
        return (Connection) attributes.get(Attribute.CLIENT);
    }

    public static PostmanClientEvent clientConnected() {
        return new PostmanClientEvent(Type.CONNECTED);
    }

    public static PostmanClientEvent clientDisconnected() {
        return new PostmanClientEvent(Type.DISCONNECTED);
    }

    public static PostmanClientEvent newMessage(@NonNull Connection client, @NonNull PostmanMessage msg) {
        return new PostmanClientEvent(Type.NEW_MESSAGE).addAttribute(Attribute.MESSAGE, msg)
                .addAttribute(Attribute.CLIENT, client);
    }
    public static PostmanClientEvent ignoreEvent() {
        return new PostmanClientEvent(Type.IGNORE);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PostmanClientEvent that = (PostmanClientEvent) o;
        return type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type);
    }
}
