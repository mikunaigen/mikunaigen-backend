package com.mikunaigen.backend.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.mikunaigen.backend.dto.CalificacionPedidoRequest;
import com.mikunaigen.backend.service.CalificacionPedidoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;


@RestController
@ConditionalOnProperty(name = "app.legacy.restaurante.habilitado", havingValue = "true")
@RequestMapping("/api/pedidos/calificacion")
public class CalificacionPedidoController {

    @Autowired
    private CalificacionPedidoService calificacionPedidoService;

    @PostMapping
    public ResponseEntity<?> enviar(@RequestBody CalificacionPedidoRequest body) {
        try {
            if (body == null || body.userId() == null || body.userId().isBlank()
                    || body.orderId() == null || body.orderId().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Datos incompletos."));
            }
            calificacionPedidoService.calificar(
                    UUID.fromString(body.userId().trim()),
                    UUID.fromString(body.orderId().trim()),
                    body.stars(),
                    body.comment());
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "No se pudo guardar la calificación."));
        }
    }
}
