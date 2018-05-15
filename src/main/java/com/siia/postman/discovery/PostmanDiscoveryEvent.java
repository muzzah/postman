package com.siia.postman.discovery;


public class PostmanDiscoveryEvent {



    public enum Type {
        FOUND,
        LOST,
        STARTED,
        STOPPED,
        ALREADY_DISCOVERING
    }
    private final Type type;

    private Object serviceDetails;

    private PostmanDiscoveryEvent(Object serviceDetails) {
        this.type = Type.FOUND;
        this.serviceDetails = serviceDetails;
    }


    private PostmanDiscoveryEvent(Type type) {
        this.type = type;
    }

    public Type type() {
        return type;
    }

    static PostmanDiscoveryEvent lost() {
        return new PostmanDiscoveryEvent(Type.LOST);
    }

    static PostmanDiscoveryEvent alreadyDiscovering() {
        return new PostmanDiscoveryEvent(Type.ALREADY_DISCOVERING);
    }

    static PostmanDiscoveryEvent discoveryStopped() {
        return new PostmanDiscoveryEvent(Type.STOPPED);
    }

    static PostmanDiscoveryEvent started() {
        return new PostmanDiscoveryEvent(Type.STARTED);
    }
    public static PostmanDiscoveryEvent found(Object serviceDetails) {
        return new PostmanDiscoveryEvent(serviceDetails);
    }

    @SuppressWarnings("unchecked")
    public <T> T serviceInfo() {
        return (T)serviceDetails;
    }

}
