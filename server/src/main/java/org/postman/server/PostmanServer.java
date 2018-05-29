package org.postman.server;

import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;

import com.google.protobuf.MessageLite;

import java.net.InetSocketAddress;
import java.util.UUID;

import io.reactivex.Flowable;

/**
 * A PostmanServer is a server which can accept {@link PostmanClient} connections and communicate using
 * {@link PostmanMessage} structure. Uses RxJava to stream events including client connection/disconnections
 * and messages sent from clients.
 */
public interface PostmanServer {

    /**
     * Returns a {@link Flowable} when subscribed to, will bind a server socket to the specified bindAddress.
     * Will then begin streaming events as needed to specify that the server is ready to accept clients as well as
     * client connection/disconnections and new messages from clients.
     *
     * If the server encounters an error and shuts down, this will be signalled through an onError call and
     * should be considered an abnormal shutdown. If onComplete is signalled, then the server shutdown due
     * to a stopServer call.
     *
     * @param bindAddress The socket address to bind to.
     * @return A flowable that when subscribed to will bind a server socket and stream events
     */
    @AnyThread
    Flowable<PostmanServerEvent> serverStart(@NonNull InetSocketAddress bindAddress);

    /**
     * Shuts down the server and disconnects any clients that are connected while releasing
     * any resources. If server is not running, this method does nothing.
     */
    @AnyThread
    void stopServer();

    /**
     * Checks to see if the server is running
     * @return True if running, otherwise false
     */
    @AnyThread
    boolean isRunning();

    /**
     * Sends a message to all clients that are currently connected. The process of sending messages is asynchronous
     * so messages will not be sent to clients until some point after this message occurs
     * @param msg The message to broadcast to all clients
     */
    @AnyThread
    void broadcastMessage(MessageLite msg);

    /**
     * Sends a message to the specified client. This operation is asynchronous.
     * If message delivery needs to be ACK'd, then this should be implemented as part of the application
     * layer protocol. If the client is not connected, then the message will be dropped.
     *
     * @param msg The message to send
     * @param client The client to send the message to.
     */
    @AnyThread
    void sendMessage(@NonNull  PostmanMessage msg, @NonNull Connection client);

    /**
     * Convenience method to send a message without wrapping it in a PostmanMessage.
     * {@see #sendMessage(PostmanMessage}
     */
    @AnyThread
    void sendMessage(@NonNull MessageLite message, @NonNull Connection client);

    /**
     * Check the number of clients connected
     * @return The number of clients currently connected to the server
     */
    @AnyThread
    int numberOfClients();

    /**
     * Convenience method to send a message without wrapping it in a PostmanMessage
     * and using the UUID of the client connected. If the UUID specified is invalid,
     * then the message will be dropped.
     *
     * {@see #sendMessage(PostmanMessage}
     */
    @AnyThread
    void sendMessage(MessageLite msg, UUID uuid);
}
