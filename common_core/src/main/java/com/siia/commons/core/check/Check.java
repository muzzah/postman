package com.siia.commons.core.check;

public final class Check {

    private Check() {}

    public static void checkState(boolean value, String msg, Object... args) {
        if(!value) {
            throw new IllegalStateException(String.format(msg, args));
        }
    }

}

