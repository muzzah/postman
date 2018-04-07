package com.siia.postman.discovery;


import android.support.annotation.NonNull;

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

    private String apName;
    private String passwd;

    private PostmanDiscoveryEvent(InetAddress address, int port, Type type) {
        this.address = address;
        this.port = port;
        this.type = type;
    }

    private PostmanDiscoveryEvent(String apName, String passwd) {
        this.apName = apName;
        this.passwd = passwd;
        this.type = Type.FOUND;
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

    public static PostmanDiscoveryEvent found(@NonNull InetAddress address, int port) {
        return new PostmanDiscoveryEvent(address, port, Type.FOUND);
    }

    static PostmanDiscoveryEvent notFound() {
        return new PostmanDiscoveryEvent(Type.LOST);
    }

    static PostmanDiscoveryEvent started() {
        return new PostmanDiscoveryEvent(Type.STARTED);
    }

    static PostmanDiscoveryEvent foundAP(@NonNull String apName, @NonNull String sharedKey) {
        return new PostmanDiscoveryEvent(apName, sharedKey);
    }

    public String getApName() {
        return apName;
    }

    public String getPasswd() {
        return passwd;
    }
}
