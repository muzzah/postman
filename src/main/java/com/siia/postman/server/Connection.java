package com.siia.postman.server;


import java.util.UUID;

public interface Connection extends Comparable<Connection> {

    UUID getConnectionId();


    boolean isValid();

}
