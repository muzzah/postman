package com.siia.postman.server;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class ServerEvent {


    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);




    private enum Attribute {
        CLIENT,
        LISTENING_PORT,
        IP_ADDRESS
    }

    public enum NetworkEventType {
        CLIENT_JOIN,
        SERVER_LISTENING,
        CLIENT_DISCONNECT,
        NEW_MESSAGE
    }


    private final NetworkEventType type;
    private final Map<Attribute, Object> attributes;
    private final ByteBuffer data;

    private ServerEvent(NetworkEventType type, ByteBuffer data) {
        this.type = type;
        this.data = data;
        this.attributes = new HashMap<>();
    }

    private ServerEvent(NetworkEventType type) {
        this(type, EMPTY_BUFFER);
    }


    public NetworkEventType type() {
        return type;
    }

    public PostmanServerClient client() {
        return (PostmanServerClient) attributes.get(Attribute.CLIENT);
    }

    public ByteBuffer data() {
        return data;
    }


    public int getListeningPort() {
        return (Integer) attributes.get(Attribute.LISTENING_PORT);
    }

    public String getHostAddress() {
        return (String)attributes.get(Attribute.IP_ADDRESS);
    }


    public boolean clientJoined() {
        return NetworkEventType.CLIENT_JOIN.equals(type);
    }

    public boolean notClientJoined() {
        return !NetworkEventType.CLIENT_JOIN.equals(type);
    }

    public boolean message() {
        return NetworkEventType.NEW_MESSAGE.equals(this);
    }

    private ServerEvent attribute(Attribute attribute, Object value) {
        attributes.put(attribute, value);
        return this;
    }

    static ServerEvent clientDisconnected(int clientId) {
        return new ServerEvent(NetworkEventType.CLIENT_DISCONNECT).attribute(Attribute.CLIENT, clientId);
    }


    public static ServerEvent serverListening(int port, String hostAddress) {
        return new ServerEvent(NetworkEventType.SERVER_LISTENING).attribute(Attribute.LISTENING_PORT, port)
                .attribute(Attribute.IP_ADDRESS, hostAddress);
    }


    public static ServerEvent newClient(PostmanServerClient postmanServerClient) {
        return new ServerEvent(NetworkEventType.CLIENT_JOIN).attribute(Attribute.CLIENT, postmanServerClient);
    }

    public static ServerEvent clientDiscommected(PostmanServerClient client) {
        return new ServerEvent(NetworkEventType.CLIENT_DISCONNECT).attribute(Attribute.CLIENT, client);
    }

    static ServerEvent newData(ByteBuffer buffer, int clientId) {
        return new ServerEvent(NetworkEventType.NEW_MESSAGE, buffer).attribute(Attribute.CLIENT, clientId);
    }
}
