package com.siia.postman.server;


import android.util.Log;

import com.osiyent.sia.commons.core.log.Logcat;

import java.nio.ByteBuffer;

import javax.inject.Inject;

public class Postman {
    private static final String TAG = Logcat.getTag();

    private PostmanServer postmanServer;

    @Inject
    public Postman(PostmanServer postmanServer) {
        this.postmanServer = postmanServer;
    }


    public void discoverServer() {
        postmanServer.discoverServerService();
    }

    public void cancelDiscovery() {
        postmanServer.cancelDiscovery();
    }

    public void startServer() {
        postmanServer.startServer(new NetworkEventListener() {
            @Override
            public void onClientJoin(int clientId) {

            }

            @Override
            public void onClientDisconnect(int clientId) {

            }

            @Override
            public void onClientData(ByteBuffer data, int clientId) {

            }

            @Override
            public void onServerListening() {
                Log.i(TAG, "Postman Server Listening");
            }
        });
    }

    public void stopServer() {
        postmanServer.stopServer();
    }
}
