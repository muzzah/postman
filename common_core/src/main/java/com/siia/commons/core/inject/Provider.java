package com.siia.commons.core.inject;

/**
 * Used to provide an instance of something. Generally used to help with unit testing
 * and you dont want to construct an instance of something during a function call.
 * @param <T> The type to return
 */
public interface Provider<T> {

    /**
     * Provides a fully-constructed and injected instance of {@code T}.
     *
     * @throws RuntimeException If the instance cannot be constructed at runtime for some reason
     */
    T get();
}
