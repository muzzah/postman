package com.siia.postman.discovery;


import java.net.InetAddress;

public class PostmanDiscoveryEvent {

    public enum Type {
        FOUND,
        LOST,
        STARTED
    }

    private InetAddress address;
    private int port;
    private final Type type;

    private PostmanDiscoveryEvent(InetAddress address, int port, Type type) {
        this.address = address;
        this.port = port;
        this.type = type;
    }

    private PostmanDiscoveryEvent(Type type) {
        this.type = type;
    }

    public String getHotname() {
        return address.getHostAddress();
    }

    public int getPort() {
        return port;
    }

    public boolean foundService() {
        return Type.FOUND.equals(type);
    }

    public boolean lostService() {
        return Type.LOST.equals(type);
    }

    public Type type() {
        return type;
    }

    static PostmanDiscoveryEvent found(InetAddress address, int port) {
        return new PostmanDiscoveryEvent(address, port, Type.FOUND);
    }

    static PostmanDiscoveryEvent notFound() {
        return new PostmanDiscoveryEvent(Type.LOST);
    }

    static PostmanDiscoveryEvent started() {
        return new PostmanDiscoveryEvent(Type.STARTED);
    }
}
