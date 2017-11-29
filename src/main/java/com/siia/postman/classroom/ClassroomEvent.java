package com.siia.postman.classroom;

public class ClassroomEvent {

    public enum ClassroomEventType {
        PARTICIPANT_JOIN,
        CLASS_STARTED,
        CLASS_ENDED,
        IGNORE
    }


    private final ClassroomEventType type;

    private ClassroomEvent(ClassroomEventType type) {
        this.type = type;
    }


    public ClassroomEventType type() {
        return type;
    }

    public  static ClassroomEvent participantJoin() {
        return new ClassroomEvent(ClassroomEventType.PARTICIPANT_JOIN);
    }


    public static ClassroomEvent classStarted() {
        return new ClassroomEvent(ClassroomEventType.CLASS_STARTED);
    }


    public static ClassroomEvent ignoreEvent() {
        return new ClassroomEvent(ClassroomEventType.IGNORE);
    }

    public boolean ignore() {
        return ClassroomEventType.IGNORE.equals(type);
    }
}
