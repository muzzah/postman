package com.siia.postman;

import com.osiyent.sia.commons.core.log.Logcat;
import com.siia.postman.server.IpBasedServer;
import com.siia.postman.server.NetworkEventListener;
import com.siia.postman.server.Server;

import java.nio.ByteBuffer;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.functions.Action1;
import rx.subjects.PublishSubject;

@Singleton
public class ClassroomSession implements NetworkEventListener {
    private static final String TAG = Logcat.getTag();
    private PublishSubject<ClassroomEvent> classroomSubject = PublishSubject.create();

    private Server server;

    @Inject
    public ClassroomSession() {
        server = new IpBasedServer();
    }

    public void startClass() {

        //Start advertising service Bonjour
        //server.startServiceAdvertising()

        server.startServer(this);
    }

    @Override
    public void onClientJoin(int clientId) {
        Logcat.i(TAG, "User Joined");
        classroomSubject.onNext(ClassroomEvent.studentJoinedEvent(clientId));
    }

    @Override
    public void onClientDisconnect(int clientId) {
        Logcat.i(TAG, "User disconnected");
        classroomSubject.onNext(ClassroomEvent.studentLeftEvent(clientId));
    }

    @Override
    public void onClientData(ByteBuffer data, int clientId) {
        Logcat.i(TAG, "User Said Something : %d", data.limit());
    }

    public void addStudentListener(Action1<ClassroomEvent> listener) {
        classroomSubject.subscribe(listener);
    }

    public void stopClass() {
        server.stopServer();
    }
}
