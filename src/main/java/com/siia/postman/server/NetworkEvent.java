package com.siia.postman.server;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class NetworkEvent {


    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    private enum Attribute {
        CLIENT_ID,
        LISTENING_PORT,
        IP_ADDRESS
    }

    public enum NetworkEventType {
        CLIENT_JOIN,
        SERVER_LISTENING,
        CLIENT_DISCONNECT,
        NEW_DATA
    }


    private final NetworkEventType type;
    private final Map<Attribute, Object> attributes;
    private final ByteBuffer data;

    private NetworkEvent(NetworkEventType type, ByteBuffer data) {
        this.type = type;
        this.data = data;
        this.attributes = new HashMap<>();
    }

    private NetworkEvent(NetworkEventType type) {
        this(type, EMPTY_BUFFER);
    }


    public NetworkEventType type() {
        return type;
    }

    public int clientId() {
        return (int) attributes.get(Attribute.CLIENT_ID);
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


    private NetworkEvent attribute(Attribute attribute, Object value) {
        attributes.put(attribute, value);
        return this;
    }

    static NetworkEvent clientDisconnected(int clientId) {
        return new NetworkEvent(NetworkEventType.CLIENT_DISCONNECT).attribute(Attribute.CLIENT_ID, clientId);
    }


    static NetworkEvent serverListening(int port, String hostAddress) {
        return new NetworkEvent(NetworkEventType.SERVER_LISTENING).attribute(Attribute.LISTENING_PORT, port)
                .attribute(Attribute.IP_ADDRESS, hostAddress);
    }


    static NetworkEvent newClient(int clientId) {
        return new NetworkEvent(NetworkEventType.CLIENT_JOIN).attribute(Attribute.CLIENT_ID, clientId);
    }

    static NetworkEvent newData(ByteBuffer buffer, int clientId) {
        return new NetworkEvent(NetworkEventType.NEW_DATA, buffer).attribute(Attribute.CLIENT_ID, clientId);
    }
}
