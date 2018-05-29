package org.postman.server;


import android.support.annotation.NonNull;

import java.util.UUID;

/**
 * Used by the {@link PostmanServer} to represent a connection to a client. Once a client
 * has established a connection, the event that the {@link PostmanServer} broadcasts will
 * contain an instance of this class that can be used.
 * A connection has a unique 128 bit UUID that is unique from other connections.
 *
 */
public interface Connection extends Comparable<Connection> {

    /**
     * The {@link UUID} that identifies this connections from other connections.
     * @return 128 bit UUID for this connection
     */
    UUID getConnectionId();

    /**
     * Disconnects this connection and cleans up any resources needed.
     */
    void disconnect();

    /**
     * Boolean determining if this connection is still active and can send/receive messages
     * @return True is connection is active, false otherwise
     */
    boolean isConnected();

    /**
     * Queues up a {@link PostmanMessage} to send over the connection to the client.
     * The message may be sent after this method returns in a non blocking way so the caller
     * should not expect that the message is sent straight away.
     * If this method is called while not connected, an exception is thrown
     * @param msg The message to send
     */
    void queueMessageToSend(PostmanMessage msg);

    @Override
    default int compareTo(@NonNull Connection o) {
        return o.getConnectionId().compareTo(getConnectionId());
    }
}
