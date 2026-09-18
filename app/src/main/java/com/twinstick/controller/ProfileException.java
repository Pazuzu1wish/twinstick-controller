package com.twinstick.controller;

/** Thrown when a profile JSON is missing, malformed, or fails validation.
 *  The message is written for humans (shown in a dialog/toast). */
public class ProfileException extends Exception {
    public ProfileException(String msg) { super(msg); }
    public ProfileException(String msg, Throwable cause) { super(msg, cause); }
}
