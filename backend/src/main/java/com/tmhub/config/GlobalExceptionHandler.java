package com.tmhub.config;

import com.tmhub.service.BadRequestException;
import com.tmhub.service.NotFoundException;
import com.tmhub.service.PublishBlockedException;
import com.tmhub.service.VersionConflictException;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    record ErrorBody(String error, String message, OffsetDateTime at) {
        static ErrorBody of(String error, String message) {
            return new ErrorBody(error, message, OffsetDateTime.now());
        }
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ErrorBody> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorBody.of("not_found", e.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<ErrorBody> badRequest(BadRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorBody.of("bad_request", e.getMessage()));
    }

    /** Concurrent review / stale version token: the reviewer UI shows a version-conflict prompt. */
    @ExceptionHandler(VersionConflictException.class)
    ResponseEntity<Map<String, Object>> versionConflict(VersionConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "version_conflict",
                "message", e.getMessage(),
                "at", OffsetDateTime.now().toString()));
    }

    /** Quarantined anomalies or open conflicts block the release. */
    @ExceptionHandler(PublishBlockedException.class)
    ResponseEntity<ErrorBody> publishBlocked(PublishBlockedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorBody.of("publish_blocked", e.getMessage()));
    }
}
