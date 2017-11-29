package com.siia.postman.discovery;


import android.net.nsd.NsdServiceInfo;

import java.net.InetAddress;

public class PostmanDiscoveryEvent {

    public enum Type {
        FOUND,
        LOST,
        STARTED
    }

    private final InetAddress address;
    private final int port;
    private final Type type;

    private PostmanDiscoveryEvent(NsdServiceInfo nsdServiceInfo, Type type) {
        this.address = nsdServiceInfo.getHost();
        this.port = nsdServiceInfo.getPort();
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

    static PostmanDiscoveryEvent found(NsdServiceInfo info) {
        return new PostmanDiscoveryEvent(info, Type.FOUND);
    }

    static PostmanDiscoveryEvent lost(NsdServiceInfo info) {
        return new PostmanDiscoveryEvent(info, Type.LOST);
    }

    static PostmanDiscoveryEvent started() {
        return new PostmanDiscoveryEvent(new NsdServiceInfo(), Type.STARTED);
    }
}
