package com.siia.commons.core.log;


import android.util.Log;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.isNull;

@SuppressWarnings("PMD.ShortMethodName")
public final class Logcat {
    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("\\.(\\w+)$");
    private static final String LOG_IDENTIFIER = "";
    private static final String LOGGER_NAME_PATTERN = LOG_IDENTIFIER + " %s";
    private static final int LOGGER_NAME_LENGTH_LIMIT = 23;

    private Logcat() {}

    public static String getTag(){
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        String fullClassNameOfRequestingClass = stackTraceElements[3].getClassName();
        Matcher matcher = CLASS_NAME_PATTERN.matcher(fullClassNameOfRequestingClass);
        if(!matcher.find()){
            return "Unidentified";
        }
        String className = matcher.group(1);
        if(loggerNameIsTooLong(className)){
            className = truncateClassName(className);
        }
        return String.format(LOGGER_NAME_PATTERN, className);
    }

    private static String truncateClassName(String className) {
        int charsAllowed = LOGGER_NAME_LENGTH_LIMIT - LOG_IDENTIFIER.length();
        return className.substring(0, charsAllowed);
    }

    private static boolean loggerNameIsTooLong(String className) {
        return String.format(LOGGER_NAME_PATTERN, className).length() > LOGGER_NAME_LENGTH_LIMIT;
    }

    public static void i(String tag, String msg) {
        Log.i(tag, insertThreadDetails(msg));
    }

    public static void i(String tag, String msg, Object... args) {
        i(tag, String.format(msg, args));
    }


    public static void d(String tag, String msg) {
        Log.d(tag, insertThreadDetails(msg));
    }

    public static void d(String tag, String msg, Object... args) {
        d(tag, String.format(msg, args));
    }

    public static void e(String tag, String msg, Throwable e) {
        Log.e(tag, insertThreadDetails(msg), e);
    }

    public static void e(String tag, String msg) {
        Log.e(tag, insertThreadDetails(msg));
    }
    public static void e(String tag, String msg, Object... args) {
        e(tag, String.format(msg, args));
    }
    public static void e(String tag, String msg, Throwable e, Object... args) {
        e(tag, String.format(msg, args), e);
    }

    public static void e(String tag, UUID id, String msg, Object... args) {
        e(tag, String.format("["+id.toString().substring(0,7)+"]" + msg, args));
    }

    public static void w(String tag, String msg, Throwable e) {
        Log.w(tag, insertThreadDetails(msg), e);
    }

    public static void w(String tag, String msg, Object... args) {
        w(tag, String.format(msg,args));
    }

    public static void w(String tag, String msg, Throwable e, Object... args) {
        Log.w(tag, insertThreadDetails(String.format(msg, args)),e);
    }

    public static void w(String tag, String msg) {
        Log.w(tag, insertThreadDetails(msg));
    }


    public static void v(String tag, String msg) {
        Log.v(tag, insertThreadDetails(msg));
    }

    public static void v(String tag, String msg, Object... args) {
        v(tag, String.format(msg, args));
    }

    public static void v(String tag, UUID id, String msg, Object... args) {
        v(tag, "["+(isNull(id) ? "NULL" : id.toString().substring(0,7)) +"] " + msg, args);
    }

    private static String insertThreadDetails(String msg) {
        return "[" + Thread.currentThread().getName() + "]]\t" + msg;
    }

    public static void result_v(String tag, String msg, boolean result) {
        v(tag, msg + " : result=%b", result);
    }

}
