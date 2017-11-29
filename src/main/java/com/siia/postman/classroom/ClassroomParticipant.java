package com.siia.postman.classroom;


public interface ClassroomParticipant {

    String firstName = "John";
    String lastName = "Doe";
    Type type = Type.TEACHER;

    enum Type {
        STUDENT,
        TEACHER;

        public boolean isTeacher() {
            return Type.TEACHER.equals(this);
        }

        public boolean isStudent() {
            return Type.STUDENT.equals(this);
        }
    }

    default String getFirstName() {
        return firstName;
    }

    default String getLastName() {
        return lastName;
    }

    default Type getType() {
        return type;
    }

}
