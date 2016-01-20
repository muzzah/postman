package com.siia.postman.server;

import java.nio.ByteBuffer;

public class NetworkEvent {


    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    public static NetworkEvent clientDisconnected(int clientId) {
        return new NetworkEvent(NetworkEventType.CLIENT_DISCONNECT, clientId);
    }

    public enum NetworkEventType {
        CLIENT_JOIN,
        CLIENT_DISCONNECT,
        NEW_DATA
    }


    private final NetworkEventType type;
    private final int clientId;
    private final ByteBuffer data;

    public NetworkEvent(NetworkEventType type, ByteBuffer data, int clientId) {
        this.type = type;
        this.clientId = clientId;
        this.data = data;
    }

    public NetworkEvent(NetworkEventType type, int clientId) {
        this(type, EMPTY_BUFFER, clientId);
    }

    public static NetworkEvent newClient(int clientId) {
        return new NetworkEvent(NetworkEventType.CLIENT_JOIN, clientId);
    }
    public static NetworkEvent newData(ByteBuffer buffer, int clientId) {
        return new NetworkEvent(NetworkEventType.NEW_DATA, buffer, clientId);
    }

    public NetworkEventType type() {
        return type;
    }

    public int clientId() {
        return clientId;
    }

    public ByteBuffer data() {
        return data;
    }
}
