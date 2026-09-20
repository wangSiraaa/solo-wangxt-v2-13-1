package com.tmhub.service;

/**
 * Raised when a review or task action loses an optimistic/pessimistic concurrency race.
 * Mapped to HTTP 409 so the reviewer UI can show a version-conflict prompt.
 */
public class VersionConflictException extends RuntimeException {
    public VersionConflictException(String message) { super(message); }
}
