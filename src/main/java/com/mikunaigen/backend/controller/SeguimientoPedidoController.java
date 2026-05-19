package com.mikunaigen.backend.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.mikunaigen.backend.dto.SeguimientoPedidoListasResponse;
import com.mikunaigen.backend.dto.SeguimientoPedidoResponse;
import com.mikunaigen.backend.service.SeguimientoPedidoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;


@RestController
@ConditionalOnProperty(name = "app.legacy.restaurante.habilitado", havingValue = "true")
@RequestMapping("/api/pedidos/seguimiento")
public class SeguimientoPedidoController {

    @Autowired
    private SeguimientoPedidoService seguimientoPedidoService;

    @GetMapping("/actual")
    public ResponseEntity<?> actual(@RequestParam("userId") String userId) {
        try {
            SeguimientoPedidoResponse r = seguimientoPedidoService.obtenerPedidoActual(UUID.fromString(userId));
            return ResponseEntity.ok(r);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "No se pudo cargar el seguimiento."));
        }
    }

    @GetMapping("/listas")
    public ResponseEntity<?> listas(@RequestParam("userId") String userId) {
        try {
            SeguimientoPedidoListasResponse r = seguimientoPedidoService.listar(UUID.fromString(userId));
            return ResponseEntity.ok(r);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "No se pudo cargar el seguimiento."));
        }
    }
}
