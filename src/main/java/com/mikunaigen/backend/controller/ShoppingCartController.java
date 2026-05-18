package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.dto.*;
import com.mikunaigen.backend.service.ShoppingCartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@RestController
@RequestMapping("/api/carrito")
public class ShoppingCartController {

    @Autowired
    private ShoppingCartService shoppingCartService;

    @GetMapping
    public ResponseEntity<?> obtener(@RequestParam String userId) {
        try {
            return ResponseEntity.ok(shoppingCartService.obtenerCarrito(userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/sugerencias-cross-sell")
    public ResponseEntity<?> sugerenciasCrossSell(@RequestParam String userId) {
        try {
            return ResponseEntity.ok(shoppingCartService.obtenerSugerenciasVentaCruzada(userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/agregar")
    public ResponseEntity<?> agregar(@RequestBody CartMutationRequest body) {
        try {
            return ResponseEntity.ok(shoppingCartService.agregarUnidad(body.userId(), body.productId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/incrementar")
    public ResponseEntity<?> incrementar(@RequestBody CartMutationRequest body) {
        try {
            return ResponseEntity.ok(shoppingCartService.incrementar(body.userId(), body.productId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/decrementar")
    public ResponseEntity<?> decrementar(@RequestBody CartMutationRequest body) {
        try {
            return ResponseEntity.ok(shoppingCartService.decrementar(body.userId(), body.productId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/eliminar")
    public ResponseEntity<?> eliminar(@RequestBody CartMutationRequest body) {
        try {
            return ResponseEntity.ok(shoppingCartService.eliminarLinea(body.userId(), body.productId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/verificar-precios")
    public ResponseEntity<?> verificarPrecios(@RequestBody VerificarPreciosRequest body) {
        try {
            return ResponseEntity.ok(shoppingCartService.verificarPrecios(body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }
}
