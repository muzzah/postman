package org.postman.discovery;

import java.net.InetAddress;

public class InetAddressServiceDetails {

    private final InetAddress host;
    private final int port;

    InetAddressServiceDetails(InetAddress host, int port) {
        this.host = host;
        this.port = port;
    }

    public InetAddress getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
