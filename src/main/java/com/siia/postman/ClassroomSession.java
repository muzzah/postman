package com.siia.postman;

import com.osiyent.sia.commons.core.log.Logcat;
import com.siia.postman.server.IpBasedServer;
import com.siia.postman.server.Server;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.functions.Action1;
import rx.subjects.PublishSubject;

@Singleton
public class ClassroomSession {
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

        server.startServer();
    }

    public void addStudentListener(Action1<ClassroomEvent> listener) {
        classroomSubject.subscribe(listener);
    }

    public void stopClass() {
        server.stopServer();
    }
}
