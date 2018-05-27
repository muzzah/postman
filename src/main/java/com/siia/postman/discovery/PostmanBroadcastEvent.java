package com.siia.postman.discovery;

import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

/**
 * Copyright Siia 2018
 */

public class PostmanBroadcastEvent {


    public enum Type {
        STARTED,
        STOPPED,
    }

    private final Type type;

    @VisibleForTesting
    public PostmanBroadcastEvent(@NonNull Type type) {
        this.type = type;
    }

    public Type type() {
        return type;
    }

    static PostmanBroadcastEvent broadcastStopped() {
        return new PostmanBroadcastEvent(Type.STOPPED);
    }

    static PostmanBroadcastEvent broadcastStarted() {
        return new PostmanBroadcastEvent(Type.STARTED);
    }

}
