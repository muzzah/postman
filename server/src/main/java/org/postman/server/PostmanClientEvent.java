package org.postman.server;


import android.support.annotation.NonNull;


import java.util.Objects;

import static java.util.Objects.nonNull;

/**
 * Represents an event that occurs for the {@link PostmanClient} once a connection request has occurred.
 * One should pay attention to what methods are called on the event. e.g No message will be present if the
 * event represents a connected event.
 */
public class PostmanClientEvent {

    public enum Type {
        //Sent once the client has successfully connected
        CONNECTED,
        //Specifies that a message has been received
        NEW_MESSAGE,
    }

    private Type type;
    private PostmanMessage msg;

    private PostmanClientEvent(@NonNull Type type, PostmanMessage msg) {
        this.type = type;
        this.msg = msg;
    }

    private PostmanClientEvent(Type type) {
        this.type = type;
    }

    public Type type() {
        return type;
    }

    public PostmanMessage msg() {
        return msg;
    }


    public boolean isNewMessageEvent() {
        return Type.NEW_MESSAGE.equals(type);
    }

    public static PostmanClientEvent isConnectedEvent() {
        return new PostmanClientEvent(Type.CONNECTED);
    }

    public static PostmanClientEvent newMessage(@NonNull PostmanMessage msg) {
        return new PostmanClientEvent(Type.NEW_MESSAGE, msg);
    }

    @Override
    public boolean equals(Object o) {
        return nonNull(o) && o instanceof PostmanClientEvent &&
                Objects.equals(type, ((PostmanClientEvent) o).type) &&
                Objects.equals(msg, ((PostmanClientEvent) o).msg);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, msg);
    }
}
