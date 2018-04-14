package com.siia.postman.discovery;

import java.net.InetAddress;

/**
 *
 * Used by postman service discovery for services which
 * Copyright Siia 2018
 */

public class InetAddressServiceDetails {

    private final InetAddress host;
    private final int port;

    public InetAddressServiceDetails(InetAddress host, int port) {
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
