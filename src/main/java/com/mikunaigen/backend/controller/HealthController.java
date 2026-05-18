package com.mikunaigen.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/ping")
    public Map<String, String> ping() {
        return Map.of("status", "awake");
    }
}