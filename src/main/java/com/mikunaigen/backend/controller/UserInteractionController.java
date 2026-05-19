package com.mikunaigen.backend.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.mikunaigen.backend.dto.InteraccionUsuarioRequest;
import com.mikunaigen.backend.service.UserInteractionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@RestController
@ConditionalOnProperty(name = "app.legacy.restaurante.habilitado", havingValue = "true")
@RequestMapping("/api/interacciones")
public class UserInteractionController {

    @Autowired
    private UserInteractionService userInteractionService;

    @PostMapping
    public ResponseEntity<?> registrar(@RequestBody InteraccionUsuarioRequest body) {
        try {
            if (body == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Body requerido."));
            }
            userInteractionService.registrar(body.userId(), body.productId(), body.action(), body.dwellTimeSeconds());
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "No se pudo registrar la interacción."));
        }
    }
}
