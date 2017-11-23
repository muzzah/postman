package com.siia.postman.server;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

class NetworkEvent {


    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    public int getListeningPort() {
        return (Integer)attributes.get(Attribute.LISTENING_PORT);
    }

    private enum Attribute {
        CLIENT_ID,
        LISTENING_PORT
    }

    static NetworkEvent clientDisconnected(int clientId) {
        return new NetworkEvent(NetworkEventType.CLIENT_DISCONNECT).attribute(Attribute.CLIENT_ID, clientId);
    }

    private NetworkEvent attribute(Attribute attribute, Object value) {
        attributes.put(attribute, value);
        return this;
    }

    static NetworkEvent serverListening(int listeningPort) {
        return new NetworkEvent(NetworkEventType.SERVER_LISTENING).attribute(Attribute.LISTENING_PORT,listeningPort);
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

    static NetworkEvent newClient(int clientId) {
        return new NetworkEvent(NetworkEventType.CLIENT_JOIN).attribute(Attribute.CLIENT_ID, clientId);
    }
    static NetworkEvent newData(ByteBuffer buffer, int clientId) {
        return new NetworkEvent(NetworkEventType.NEW_DATA, buffer).attribute(Attribute.CLIENT_ID, clientId);
    }

    NetworkEventType type() {
        return type;
    }

    int clientId() {
        return (int)attributes.get(Attribute.CLIENT_ID);
    }

    public ByteBuffer data() {
        return data;
    }
}
