package com.siia.postman.server;


import android.support.annotation.NonNull;

import org.apache.commons.lang3.builder.EqualsBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.nonNull;

public class PostmanClientEvent {

    public enum Type {
        CONNECTED,
        NEW_MESSAGE,
    }

    private enum Attribute {
        MESSAGE
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


    public boolean isNewMessage() {
        return Type.NEW_MESSAGE.equals(type);
    }

    public static PostmanClientEvent clientConnected() {
        return new PostmanClientEvent(Type.CONNECTED);
    }

    public static PostmanClientEvent newMessage(@NonNull PostmanMessage msg) {
        return new PostmanClientEvent(Type.NEW_MESSAGE).addAttribute(Attribute.MESSAGE, msg);
    }

    @Override
    public boolean equals(Object o) {
        return nonNull(o) && o instanceof PostmanClientEvent
                && new EqualsBuilder()
                .append(type, ((PostmanClientEvent) o).type)
                .append(attributes, ((PostmanClientEvent) o).attributes)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, attributes);
    }
}
