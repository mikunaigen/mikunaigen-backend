package com.mikunaigen.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/estado_bases_datos")
public class EstadoBaseDatosController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping
    public Map<String, String> checkStatus() {
        Map<String, String> status = new LinkedHashMap<>();
        try {
            jdbcTemplate.execute("SELECT 1");
            status.put("postgresql", "Conectado");
        } catch (Exception e) {
            status.put("postgresql", "Error: " + e.getMessage());
        }
        status.put("backend_status", "En ejecución");
        return status;
    }
}
