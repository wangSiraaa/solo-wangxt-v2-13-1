package com.acme.tm.error;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, what);
    }

    public static ApiException badRequest(String what) {
        return new ApiException(HttpStatus.BAD_REQUEST, what);
    }

    /** 409 — optimistic-lock / state conflict, e.g. two reviewers on the same candidate. */
    public static ApiException conflict(String what) {
        return new ApiException(HttpStatus.CONFLICT, what);
    }

    /** 422 — semantic rejection, e.g. polysemous term without a human choice. */
    public static ApiException unprocessable(String what) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, what);
    }
}
