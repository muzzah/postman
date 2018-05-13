package com.siia.postman.server;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static java.util.Objects.nonNull;

public class PostmanServerEvent {


    private enum Attribute {
        CLIENT,
        LISTENING_PORT,
        IP_ADDRESS,
        MESSAGE
    }

    public enum Type {
        CLIENT_JOIN,
        SERVER_LISTENING,
        CLIENT_DISCONNECT,
        NEW_MESSAGE
    }


    private final Type type;
    private final Map<Attribute, Object> attributes;

    private PostmanServerEvent(Type type) {
        this.type = type;
        this.attributes = new HashMap<>();
    }


    public Type type() {
        return type;
    }

    public boolean isNewMessage() {
        return Type.NEW_MESSAGE.equals(type);
    }

    public Connection connection() {
        return (Connection) attributes.get(Attribute.CLIENT);
    }

    public UUID connectionId() {
        return connection().getConnectionId();
    }

    public PostmanMessage message() {
        return (PostmanMessage) attributes.get(Attribute.MESSAGE);
    }


    public int getListeningPort() {
        return (Integer) attributes.get(Attribute.LISTENING_PORT);
    }

    public String getHostAddress() {
        return (String) attributes.get(Attribute.IP_ADDRESS);
    }


    private PostmanServerEvent attribute(Attribute attribute, Object value) {
        attributes.put(attribute, value);
        return this;
    }

    public static PostmanServerEvent serverListening(int port, String hostAddress) {
        return new PostmanServerEvent(Type.SERVER_LISTENING).attribute(Attribute.LISTENING_PORT, port)
                .attribute(Attribute.IP_ADDRESS, hostAddress);
    }


    public static PostmanServerEvent newClient(Connection connection) {
        return new PostmanServerEvent(Type.CLIENT_JOIN).attribute(Attribute.CLIENT, connection);
    }

    public static PostmanServerEvent clientDisconnected(Connection client) {
        return new PostmanServerEvent(Type.CLIENT_DISCONNECT).attribute(Attribute.CLIENT, client);
    }

    public static PostmanServerEvent newMessage(PostmanMessage msg, Connection client) {
        return new PostmanServerEvent(Type.NEW_MESSAGE).attribute(Attribute.MESSAGE, msg).attribute(Attribute.CLIENT, client);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(type).append(attributes).toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return nonNull(obj) && obj instanceof PostmanServerEvent &&
                new EqualsBuilder().append(type, ((PostmanServerEvent) obj).type)
                        .append(attributes, ((PostmanServerEvent) obj).attributes).isEquals();
    }

    @Override
    public String toString() {
        return "PostmanServerEvent{" +
                "type=" + type +
                ", attributes=" + attributes +
                '}';
    }
}
