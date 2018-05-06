package com.siia.postman.server;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static java.util.Objects.nonNull;

public class ServerEvent {

    private enum Attribute {
        CLIENT,
        LISTENING_PORT,
        IP_ADDRESS,
        CLIENT_COUNT, MESSAGE
    }

    public enum Type {
        CLIENT_JOIN,
        SERVER_LISTENING,
        CLIENT_DISCONNECT,
        NEW_MESSAGE
    }


    private final Type type;
    private final Map<Attribute, Object> attributes;

    private ServerEvent(Type type) {
        this.type = type;
        this.attributes = new HashMap<>();
    }

    public Integer numberOfClients() {
        return (Integer) attributes.get(Attribute.CLIENT_COUNT);
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


    private ServerEvent attribute(Attribute attribute, Object value) {
        attributes.put(attribute, value);
        return this;
    }

    public static ServerEvent serverListening(int port, String hostAddress) {
        return new ServerEvent(Type.SERVER_LISTENING).attribute(Attribute.LISTENING_PORT, port)
                .attribute(Attribute.IP_ADDRESS, hostAddress);
    }


    public static ServerEvent newClient(Connection connection, int numberOfClients) {
        return new ServerEvent(Type.CLIENT_JOIN).attribute(Attribute.CLIENT, connection).attribute(Attribute.CLIENT_COUNT, numberOfClients);
    }

    //TODO remove this and separate out the stream of events
    public static ServerEvent newClient(Connection connection) {
        return new ServerEvent(Type.CLIENT_JOIN).attribute(Attribute.CLIENT, connection);
    }

    public static ServerEvent clientDisconnected(Connection client) {
        return new ServerEvent(Type.CLIENT_DISCONNECT).attribute(Attribute.CLIENT, client);
    }

    public static ServerEvent newMessage(PostmanMessage msg, Connection client) {
        return new ServerEvent(Type.NEW_MESSAGE).attribute(Attribute.MESSAGE, msg).attribute(Attribute.CLIENT, client);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(type).append(attributes).toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return nonNull(obj) && obj instanceof ServerEvent &&
                new EqualsBuilder().append(type, ((ServerEvent) obj).type)
                        .append(attributes, ((ServerEvent) obj).attributes).isEquals();
    }

    @Override
    public String toString() {
        return "ServerEvent{" +
                "type=" + type +
                ", attributes=" + attributes +
                '}';
    }
}
