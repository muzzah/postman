package com.siia.postman.server;


public class PostmanClientEvent {




    public enum Type {
        CONNECTED,
        DISCONNECTED
    }

    private Type type;

    private PostmanClientEvent(Type type) {
        this.type = type;
    }


    public Type type() {
        return type;
    }

    public static PostmanClientEvent clientConnected() {
        return new PostmanClientEvent(Type.CONNECTED);
    }

    public static PostmanClientEvent clientDisconnected() {
        return new PostmanClientEvent(Type.DISCONNECTED);
    }
}
