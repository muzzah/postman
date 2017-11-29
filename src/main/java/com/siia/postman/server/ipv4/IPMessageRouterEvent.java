package com.siia.postman.server.ipv4;

class IPMessageRouterEvent {

    enum Type {
        CLIENT_REGISTERED,
        CLIENT_UNREGISTERED
    }

    private IPPostmanServerClient client;

    private Type type;

    private IPMessageRouterEvent(IPPostmanServerClient client, Type type) {
        this.client = client;
        this.type = type;
    }

    Type type() {
        return type;
    }

    IPPostmanServerClient client() {return client; }

    public static IPMessageRouterEvent clientRegistered(IPPostmanServerClient client) {
        return new IPMessageRouterEvent(client, Type.CLIENT_REGISTERED);
    }

    public static IPMessageRouterEvent clientUnregistered(IPPostmanServerClient client) {
        return new IPMessageRouterEvent(client, Type.CLIENT_UNREGISTERED);
    }

}
