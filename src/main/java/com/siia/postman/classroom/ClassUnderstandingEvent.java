package com.siia.postman.classroom;

import java.util.EnumMap;

public class ClassUnderstandingEvent {

    private int numberOfAnswers;
    private EnumMap<ClassroomOperations.Answer, Integer> answers;


    public ClassUnderstandingEvent(int numberOfAnswers) {
        this.numberOfAnswers = numberOfAnswers;
        this.answers = new EnumMap<>(ClassroomOperations.Answer.class);
    }

}
