package com.siia.postman.server;


import java.util.UUID;

public interface Connection extends Comparable<Connection> {

    int CONNECTION_TIME_PREFERENCE = 0;
    int LATENCY_PREFERENCE = 1;
    int BANDWIDTH_PREFERENCE = 1;

    UUID getConnectionId();


    boolean isValid();

}
