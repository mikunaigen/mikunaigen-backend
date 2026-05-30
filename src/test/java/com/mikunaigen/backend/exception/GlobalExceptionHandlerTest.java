package com.mikunaigen.backend.exception;

import com.mikunaigen.backend.service.RegistroErrorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(mock(RegistroErrorService.class));
    }

    @Test
    void handleResponseStatusException_returnsStatusAndBody() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solicitud inválida");

        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatusException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .containsEntry("status", 400)
                .containsEntry("message", "Solicitud inválida");
        assertThat(response.getBody().get("error").toString()).contains("400");
    }

    @Test
    void handleAllExceptions_returnsInternalServerError() {
        Exception ex = new RuntimeException("Error inesperado");

        ResponseEntity<Map<String, Object>> response = handler.handleAllExceptions(ex, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody())
                .containsEntry("status", 500)
                .containsEntry("error", "Internal Server Error")
                .containsEntry("message", "Error inesperado");
    }
}
