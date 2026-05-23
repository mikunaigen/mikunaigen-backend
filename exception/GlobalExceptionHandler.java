package com.mikunaigen.backend.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Excepción controlada en API: Estado={}, Motivo={}", 
                ex.getStatusCode(), ex.getReason(), ex);
        
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(Map.of(
                    "status", ex.getStatusCode().value(),
                    "error", ex.getStatusCode().toString(),
                    "message", ex.getReason() != null ? ex.getReason() : ex.getMessage()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAllExceptions(Exception ex) {
        log.error("Excepción no controlada en el sistema", ex);
        
        return ResponseEntity
                .status(500)
                .body(Map.of(
                    "status", 500,
                    "error", "Internal Server Error",
                    "message", ex.getMessage() != null ? ex.getMessage() : "Ocurrió un error inesperado en el servidor"
                ));
    }
}