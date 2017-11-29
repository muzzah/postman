package com.siia.postman;

import com.siia.postman.classroom.ClassroomOperations;

import javax.inject.Singleton;

import dagger.Component;

@Component(modules = {PostmanDaggerModule.class})
@Singleton
public interface PostmanDaggerComponent {

    ClassroomOperations classroom();

}
