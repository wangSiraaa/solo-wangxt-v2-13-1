package com.tmhub.service;

/** Raised when a publish is attempted while quarantined anomalies or open conflicts remain. */
public class PublishBlockedException extends RuntimeException {
    public PublishBlockedException(String message) { super(message); }
}
