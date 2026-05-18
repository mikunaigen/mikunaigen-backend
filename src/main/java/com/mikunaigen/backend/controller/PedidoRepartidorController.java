package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.dto.RepartidorOrdenCard;
import com.mikunaigen.backend.dto.RepartidorOrdenDetalle;
import com.mikunaigen.backend.service.PedidoRepartidorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;


@RestController
@RequestMapping("/api/pedidos/repartidor")
public class PedidoRepartidorController {

    @Autowired
    private PedidoRepartidorService pedidoRepartidorService;

    @GetMapping("/tablero")
    public ResponseEntity<?> tablero(@RequestParam("userId") String userId) {
        try {
            List<RepartidorOrdenCard> list = pedidoRepartidorService.listarTablero(UUID.fromString(userId));
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "No se pudo cargar el tablero."));
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<?> detalle(@PathVariable("orderId") String orderId, @RequestParam("userId") String userId) {
        try {
            RepartidorOrdenDetalle d = pedidoRepartidorService.detalle(UUID.fromString(orderId), UUID.fromString(userId));
            return ResponseEntity.ok(d);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "No se pudo cargar el detalle."));
        }
    }

    @PostMapping("/{orderId}/asumir")
    public ResponseEntity<?> asumir(@PathVariable("orderId") String orderId, @RequestBody Map<String, String> body) {
        try {
            String userId = body != null ? body.get("userId") : null;
            if (userId == null || userId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "userId requerido."));
            }
            pedidoRepartidorService.asumir(UUID.fromString(orderId), UUID.fromString(userId));
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "No se pudo asumir la orden."));
        }
    }

    @PostMapping("/{orderId}/deshacer-asumido")
    public ResponseEntity<?> deshacerAsumido(@PathVariable("orderId") String orderId, @RequestBody Map<String, String> body) {
        try {
            String userId = body != null ? body.get("userId") : null;
            if (userId == null || userId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "userId requerido."));
            }
            pedidoRepartidorService.deshacerAsumido(UUID.fromString(orderId), UUID.fromString(userId));
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "No se pudo deshacer la asignación."));
        }
    }

    @PostMapping("/{orderId}/en-camino")
    public ResponseEntity<?> enCamino(@PathVariable("orderId") String orderId, @RequestBody Map<String, String> body) {
        try {
            String userId = body != null ? body.get("userId") : null;
            if (userId == null || userId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "userId requerido."));
            }
            pedidoRepartidorService.marcarEnCamino(UUID.fromString(orderId), UUID.fromString(userId));
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "No se pudo pasar a en camino."));
        }
    }

    @PostMapping(value = "/{orderId}/entregar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> entregar(
            @PathVariable("orderId") String orderId,
            @RequestParam("userId") String userId,
            @RequestParam("pruebaEntrega") MultipartFile pruebaEntrega
    ) {
        try {
            byte[] bytes = pruebaEntrega.getBytes();
            String ct = pruebaEntrega.getContentType();
            pedidoRepartidorService.marcarEntregado(UUID.fromString(orderId), UUID.fromString(userId), bytes, ct);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "No se pudo registrar entrega."));
        }
    }
}
