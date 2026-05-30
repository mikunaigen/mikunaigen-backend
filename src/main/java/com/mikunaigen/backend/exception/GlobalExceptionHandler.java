package com.mikunaigen.backend.exception;

import com.mikunaigen.backend.service.RegistroErrorService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final RegistroErrorService registroErrorService;

    public GlobalExceptionHandler(RegistroErrorService registroErrorService) {
        this.registroErrorService = registroErrorService;
    }

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
    public ResponseEntity<Map<String, Object>> handleAllExceptions(Exception ex, WebRequest request) {
        log.error("Excepción no controlada en el sistema", ex);
        String ruta = extraerRuta(request);
        registroErrorService.registrarErrorBackend(ex, ruta, null);

        return ResponseEntity
                .status(500)
                .body(Map.of(
                    "status", 500,
                    "error", "Internal Server Error",
                    "message", ex.getMessage() != null ? ex.getMessage() : "Ocurrió un error inesperado en el servidor"
                ));
    }

    private String extraerRuta(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            HttpServletRequest http = servletWebRequest.getRequest();
            if (http != null) {
                return http.getRequestURI();
            }
        }
        return null;
    }
}
