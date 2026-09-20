package com.acme.tm.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> api(ApiException ex) {
        return build(ex.getStatus(), ex.getMessage(), null);
    }

    @ExceptionHandler(BlockedPublishException.class)
    public ResponseEntity<Map<String, Object>> blocked(BlockedPublishException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), Map.of("blockers", ex.getBlockers()));
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message, Map<String, Object> extra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        if (extra != null) body.putAll(extra);
        return ResponseEntity.status(status).body(body);
    }
}
