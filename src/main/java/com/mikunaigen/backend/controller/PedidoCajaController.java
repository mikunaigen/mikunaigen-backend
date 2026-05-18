package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.dto.CajaOrdenDetalle;
import com.mikunaigen.backend.dto.CajaOrdenListaItem;
import com.mikunaigen.backend.service.PedidoCajaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;


@RestController
@RequestMapping("/api/pedidos/caja")
public class PedidoCajaController {

    @Autowired
    private PedidoCajaService pedidoCajaService;

    @GetMapping("/pendientes")
    public ResponseEntity<?> pendientes(@RequestParam("processorUserId") String processorUserId) {
        try {
            UUID pid = UUID.fromString(processorUserId);
            List<CajaOrdenListaItem> list = pedidoCajaService.listarPendientesValidacion(pid);
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "No se pudo obtener la lista."));
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<?> detalle(
            @PathVariable("orderId") String orderId,
            @RequestParam("processorUserId") String processorUserId
    ) {
        try {
            UUID oid = UUID.fromString(orderId);
            UUID pid = UUID.fromString(processorUserId);
            CajaOrdenDetalle d = pedidoCajaService.obtenerDetalle(oid, pid);
            return ResponseEntity.ok(d);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "No se pudo cargar el pedido."));
        }
    }

    @PostMapping("/{orderId}/validar")
    public ResponseEntity<?> validar(
            @PathVariable("orderId") String orderId,
            @RequestBody Map<String, String> body
    ) {
        try {
            String puid = body != null ? body.get("processorUserId") : null;
            if (puid == null || puid.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "processorUserId requerido."));
            }
            UUID oid = UUID.fromString(orderId);
            UUID pid = UUID.fromString(puid);
            pedidoCajaService.validarPago(oid, pid);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "No se pudo validar el pago."));
        }
    }

    @PostMapping("/{orderId}/rechazar")
    public ResponseEntity<?> rechazar(
            @PathVariable("orderId") String orderId,
            @RequestBody Map<String, String> body
    ) {
        try {
            String puid = body != null ? body.get("processorUserId") : null;
            String motivo = body != null ? body.get("motivo") : null;
            if (puid == null || puid.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "processorUserId requerido."));
            }
            UUID oid = UUID.fromString(orderId);
            UUID pid = UUID.fromString(puid);
            pedidoCajaService.rechazarPago(oid, pid, motivo != null ? motivo : "");
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "No se pudo rechazar el pago."));
        }
    }
}
