package org.postman.discovery;


public class PostmanDiscoveryEvent {

    public enum Type {
        FOUND,
        LOST,
        STARTED,
    }
    private final Type type;

    private Object serviceDetails;

    PostmanDiscoveryEvent(Object serviceDetails) {
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

    static PostmanDiscoveryEvent started() {
        return new PostmanDiscoveryEvent(Type.STARTED);
    }

    static PostmanDiscoveryEvent found(Object serviceDetails) {
        return new PostmanDiscoveryEvent(serviceDetails);
    }

    @SuppressWarnings("unchecked")
    public <T> T serviceInfo() {
        return (T)serviceDetails;
    }

}
