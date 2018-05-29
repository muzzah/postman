package com.siia.commons.core.io;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import static java.util.Objects.isNull;

public class IO {
    private IO() {}

    public static void closeQuietly(SocketChannel channel) {
        if(isNull(channel)) {
            return;
        }
        closeQuietly(channel.socket());
        try {
            channel.close();
        } catch (Throwable e) {
            //Quietly
        }
    }

    private static void closeQuietly(Socket socket) {
        if(isNull(socket)) {
            return;
        }

        try {
            socket.close();
        } catch (Throwable e) {
            //Quietly
        }
    }

    public static void closeQuietly(ServerSocketChannel channel) {
        if(isNull(channel)) {
            return;
        }

        closeQuietly(channel.socket());
        try {
            channel.close();
        } catch (Throwable e) {
            //Quietly
        }
    }

    public static void closeQuietly(Selector selector) {
        if(isNull(selector)) {
            return;
        }
        try {
            selector.close();
        } catch (Throwable e) {
            //Quietly
        }
    }


    public static void closeQuietly(ServerSocket socket) {
        if(isNull(socket)) {
            return;
        }

        try{
            socket.close();
        } catch(Throwable e) {
            //Quietly
        }
    }

    public static int copyUntilDestinationFull(ByteBuffer src, ByteBuffer dest) {
        if(dest.remaining() >= src.remaining()) {
            int bytes = src.remaining();
            dest.put(src);
            return bytes;
        } else if(dest.remaining() < src.remaining()) {
            byte[] buffer = new byte[dest.remaining()];
            src.get(buffer);
            dest.put(buffer);
            return buffer.length;
        }

        return 0;
    }
}
