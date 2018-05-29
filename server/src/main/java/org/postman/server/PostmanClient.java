package org.postman.server;


import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;

import com.google.protobuf.MessageLite;

import java.net.InetAddress;
import java.nio.channels.SocketChannel;


import io.reactivex.Flowable;

/**
 * Class to be used when wanting to make a client connection to a {@link PostmanServer}.
 * Uses a reactive stream {@link Flowable} to send out events once a connection request has been made
 * though the connect method will only start sending events once the {@link Flowable} has been subscribed to.
 * Its important to note that the signal for a disconnection will come through as a onComplete or onError call.
 * See {@link PostmanClientEvent} for more info.
 */
public interface PostmanClient {


    /**
     * Returns a {@link Flowable} that will connect to the specified host and port once subscribed to.
     * The flowable will be operating on the IO scheduler provided by the RX framework and will send through
     * events as connection occurs and messages are received. An onError or onComplete signals specifies
     * that a disconnection has occurred.
     *
     * Once this method is called, no changes should be made to the SocketChannel.
     *
     * @param socketChannel The socket channel to use when connecting to the client
     * @param host The host to connect to
     * @param port The post to connect to on the host.
     * @return A Flowable that when subscribed will attempt to connect to the specified server
     */
    @AnyThread
    Flowable<PostmanClientEvent> connect(@NonNull SocketChannel socketChannel, @NonNull InetAddress host, int port);

    /**
     * Add message to be sent. Messages are not guaranteed to be sent once this method returns (thus sending messages
     * are asynchronous operations).
     * The message can be sent at some point after this method returns though an effort should be made
     * to ensure that it is sent as soon as possible. If one needs to ACK the message sent then that
     * should be handled as part of the application layer protocol.
     *
     * @param msg the message to send
     */
    @AnyThread
    void sendMessage(@NonNull PostmanMessage msg);

    /**
     * Convenience method to add a protocol buffers message to be sent. This will be wrapped into a {@link PostmanMessage}
     * with the same behavior as {@see #sendMessage(PostmanMessage)}
     *
     * @param msg the message to send
     */
    @AnyThread
    void sendMessage(@NonNull MessageLite msg);

    /**
     * Disconnects the client and cleans up any resources.
     */
    @AnyThread
    void disconnect();

    /**
     * Checks to see if the client is still connected.
     * @return True if connected, false otherwise
     */
    @AnyThread
    boolean isConnected();

}
