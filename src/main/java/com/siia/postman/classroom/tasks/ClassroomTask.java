package com.siia.postman.classroom.tasks;


import io.reactivex.subjects.ReplaySubject;

public interface ClassroomTask<T> {

    ReplaySubject<?> startTask();

    void cancelTask();
}
