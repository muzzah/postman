package com.siia.postman.server.nio;

class MessageQueueEvent {

    enum Type {
        CLIENT_REGISTERED,
        CLIENT_REGISTRATION_FAILED,
        CLIENT_UNREGISTERED
    }

    private ServerClient client;

    private Type type;

    private MessageQueueEvent(ServerClient client, Type type) {
        this.client = client;
        this.type = type;
    }

    Type type() {
        return type;
    }

    ServerClient client() {return client; }

    static MessageQueueEvent clientRegistered(ServerClient client) {
        return new MessageQueueEvent(client, Type.CLIENT_REGISTERED);
    }

    static MessageQueueEvent clientUnregistered(ServerClient client) {
        return new MessageQueueEvent(client, Type.CLIENT_UNREGISTERED);
    }

    static MessageQueueEvent clientRegistrationFailed(ServerClient client) {
        return new MessageQueueEvent(client, Type.CLIENT_REGISTRATION_FAILED);
    }


}
