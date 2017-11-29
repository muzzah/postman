package com.siia.postman.server;

import java.nio.ByteBuffer;

public class PostmanMessage {
    private String msg;

    public PostmanMessage(String msg) {
        this.msg = msg;
    }

    public String getMsg() {
        return msg;
    }

    public ByteBuffer serialise() {
        return ByteBuffer.wrap(msg.getBytes());
    }

    @Override
    public String toString() {
        return "PostmanMessage{" +
                "msg='" + msg + '\'' +
                '}';
    }
}

