package com.mikunaigen.backend.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.mikunaigen.backend.service.PedidoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;


@RestController
@ConditionalOnProperty(name = "app.legacy.restaurante.habilitado", havingValue = "true")
@RequestMapping("/api/pedidos")
public class PedidoController {

    @Autowired
    private PedidoService pedidoService;

    @PostMapping(value = "/checkout", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> checkout(
            @RequestParam("userId") String userId,
            @RequestParam("comprobante") MultipartFile comprobante
    ) {
        try {
            UUID uid = UUID.fromString(userId);
            byte[] bytes = comprobante.getBytes();
            String ct = comprobante.getContentType();
            Map<String, Object> body = pedidoService.crearPedidoConComprobante(uid, bytes, ct);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "No se pudo registrar el pedido."));
        }
    }
}
