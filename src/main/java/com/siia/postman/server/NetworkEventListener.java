package com.siia.postman.server;

import java.nio.ByteBuffer;

interface NetworkEventListener  {
    void onClientJoin(int clientId);
    void onClientDisconnect(int clientId);
    void onClientData(ByteBuffer data, int clientId);
    void onServerListening();
}
