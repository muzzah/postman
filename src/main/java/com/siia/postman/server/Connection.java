package com.siia.postman.server;


import java.util.UUID;

public interface Connection {

    UUID getClientId();


    boolean isValid();

    static String logMsg(String msg, Object... args) {
        return String.format( "[%s] " + msg, args);
    }

}
