package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.dto.CocinaOrdenCard;
import com.mikunaigen.backend.service.PedidoCocinaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;


@RestController
@RequestMapping("/api/pedidos/cocina")
public class PedidoCocinaController {

    @Autowired
    private PedidoCocinaService pedidoCocinaService;

    @GetMapping("/tablero")
    public ResponseEntity<?> tablero(@RequestParam("userId") String userId) {
        try {
            List<CocinaOrdenCard> list = pedidoCocinaService.listarTablero(UUID.fromString(userId));
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "No se pudo cargar el tablero."));
        }
    }

    @PostMapping("/{orderId}/validar-stock")
    public ResponseEntity<?> validarStock(@PathVariable("orderId") String orderId, @RequestBody Map<String, String> body) {
        try {
            String uid = body != null ? body.get("userId") : null;
            if (uid == null || uid.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "userId requerido."));
            }
            pedidoCocinaService.validarStockParaPreparacion(UUID.fromString(orderId), UUID.fromString(uid));
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "No se pudo validar stock."));
        }
    }

    @PostMapping("/{orderId}/iniciar")
    public ResponseEntity<?> iniciar(@PathVariable("orderId") String orderId, @RequestBody Map<String, String> body) {
        try {
            String uid = body != null ? body.get("userId") : null;
            if (uid == null || uid.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "userId requerido."));
            }
            pedidoCocinaService.confirmarPasoPreparacion(UUID.fromString(orderId), UUID.fromString(uid));
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "No se pudo mover la orden."));
        }
    }

    @PostMapping("/{orderId}/listo")
    public ResponseEntity<?> listo(@PathVariable("orderId") String orderId, @RequestBody Map<String, String> body) {
        try {
            String uid = body != null ? body.get("userId") : null;
            if (uid == null || uid.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "userId requerido."));
            }
            pedidoCocinaService.marcarListo(UUID.fromString(orderId), UUID.fromString(uid));
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "No se pudo marcar la orden como lista."));
        }
    }
}
