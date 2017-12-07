package com.siia.postman.server;


import java.util.UUID;

public interface Connection extends Comparable<Connection> {

    UUID getConnectionId();


    boolean isValid();

    static String logMsg(String msg, Object... args) {
        return String.format( "[%s] " + msg, args);
    }

}
