package com.siia.postman.server;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class ServerEvent {


    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);



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

    private ServerEvent(Type type, ByteBuffer data) {
        this.type = type;
        this.attributes = new HashMap<>();
    }

    private ServerEvent(Type type) {
        this(type, EMPTY_BUFFER);
    }


    public Type type() {
        return type;
    }

    public boolean isNewMessageFor() {
        return Type.NEW_MESSAGE.equals(type);
    }

    boolean isNewMessageFor(Connection client) {
        return Type.NEW_MESSAGE.equals(type) && client().equals(client);
    }

    public Connection client() {
        return (Connection) attributes.get(Attribute.CLIENT);
    }

    public PostmanMessage message() {
        return (PostmanMessage) attributes.get(Attribute.MESSAGE);
    }


    public int getListeningPort() {
        return (Integer) attributes.get(Attribute.LISTENING_PORT);
    }

    public String getHostAddress() {
        return (String)attributes.get(Attribute.IP_ADDRESS);
    }


    private ServerEvent attribute(Attribute attribute, Object value) {
        attributes.put(attribute, value);
        return this;
    }

    static ServerEvent clientDisconnected(int clientId) {
        return new ServerEvent(Type.CLIENT_DISCONNECT).attribute(Attribute.CLIENT, clientId);
    }


    public static ServerEvent serverListening(int port, String hostAddress) {
        return new ServerEvent(Type.SERVER_LISTENING).attribute(Attribute.LISTENING_PORT, port)
                .attribute(Attribute.IP_ADDRESS, hostAddress);
    }


    public static ServerEvent newClient(Connection connection) {
        return new ServerEvent(Type.CLIENT_JOIN).attribute(Attribute.CLIENT, connection);
    }

    public static ServerEvent clientDisconnected(Connection client) {
        return new ServerEvent(Type.CLIENT_DISCONNECT).attribute(Attribute.CLIENT, client);
    }

    public static ServerEvent newMessage(PostmanMessage msg, Connection client) {
        return new ServerEvent(Type.NEW_MESSAGE).attribute(Attribute.MESSAGE, msg).attribute(Attribute.CLIENT, client);
    }
}
