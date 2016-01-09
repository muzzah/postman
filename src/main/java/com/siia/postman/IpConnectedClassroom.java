package com.siia.postman;

import android.util.Log;

import com.osiyent.sia.commons.core.io.IO;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.schedulers.Schedulers;

public class IpConnectedClassroom implements Classroom {
    private static final String TAG = IpConnectedClassroom.class.getSimpleName();

    @Override
    public void startClass() {
        Observable.create(new OnSubscribe<ByteBuffer>() {
            @Override
            public void call(Subscriber<? super ByteBuffer> subscriber) {
                Log.i(TAG, "Opening ServerSocket");
                ServerSocketChannel serverSocket;
                try {
                    serverSocket = ServerSocketChannel.open();
                    serverSocket.socket().bind(new InetSocketAddress("0.0.0.0", 54345));
                } catch (IOException e) {
                    Log.e(TAG, "Couldnt start server", e);
                    subscriber.onCompleted();
                    return;
                }
                Log.i(TAG, "Waiting for client");
                SocketChannel socketChannel = null;
                while(socketChannel == null) {
                    try {
                        socketChannel = serverSocket.accept();
                    } catch (IOException e) {
                        Log.e(TAG, "Problem accepting client", e);
                        IO.closeQuietly(serverSocket);
                        subscriber.onCompleted();
                        return;

                    }
                }
                Log.i(TAG, "Waiting for msg");
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                try {
                    socketChannel.read(buffer);
                    buffer.rewind();
                    socketChannel.write(buffer);
                } catch (IOException e) {
                    Log.e(TAG, "Error when reading from client", e);

                } finally {
                    IO.closeQuietly(socketChannel);
                    IO.closeQuietly(serverSocket);
                    subscriber.onCompleted();

                }

            }
        }).observeOn(Schedulers.newThread()).subscribeOn(Schedulers.newThread()).subscribe(new Subscriber<ByteBuffer>() {
            @Override
            public void onCompleted() {
                Log.i(TAG, "Server Work Complete");
            }

            @Override
            public void onError(Throwable e) {
                Log.e(TAG, "Error", e);
            }

            @Override
            public void onNext(ByteBuffer byteBuffer) {

            }
        });

    }

    @Override
    public void startClass2() {
        Observable.create(new OnSubscribe<ByteBuffer>() {
            @Override
            public void call(Subscriber<? super ByteBuffer> subscriber) {
                Log.i(TAG, "Opening Client connection");
                SocketChannel socketChannel;
                try {
                    socketChannel = SocketChannel.open(new InetSocketAddress("10.0.2.2", 54345));
                } catch (Exception e) {
                    Log.e(TAG, "Couldnt connect client", e);
                    subscriber.onCompleted();
                    return;
                }
                if(!socketChannel.isConnected()) {
                    Log.e(TAG, "Not connected");
                    subscriber.onCompleted();
                    return;
                }

                Log.i(TAG, "Writing msg");
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                try {

                    socketChannel.write((ByteBuffer) buffer.put("Hello World".getBytes()).rewind());
                } catch (IOException e) {
                    Log.e(TAG, "Error when writing msg", e);
                } finally {
                    IO.closeQuietly(socketChannel);
                    subscriber.onCompleted();
                }

            }
        }).observeOn(Schedulers.newThread()).subscribeOn(Schedulers.newThread()).subscribe(new Subscriber<ByteBuffer>() {
            @Override
            public void onCompleted() {
                Log.i(TAG, "Client Work Complete");
            }

            @Override
            public void onError(Throwable e) {
                Log.e(TAG, "Error", e);
            }

            @Override
            public void onNext(ByteBuffer byteBuffer) {

            }
        });
    }


}
