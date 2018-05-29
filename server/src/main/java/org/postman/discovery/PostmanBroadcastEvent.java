package org.postman.discovery;

import android.support.annotation.NonNull;

public class PostmanBroadcastEvent {


    public enum Type {
        STARTED
    }

    private final Type type;

    PostmanBroadcastEvent(@NonNull Type type) {
        this.type = type;
    }

    public Type type() {
        return type;
    }

    static PostmanBroadcastEvent broadcastStarted() {
        return new PostmanBroadcastEvent(Type.STARTED);
    }

}
