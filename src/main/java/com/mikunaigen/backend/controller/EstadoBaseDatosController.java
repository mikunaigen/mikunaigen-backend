package com.mikunaigen.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
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

    @Autowired
    private MongoTemplate mongoTemplate;

    @GetMapping
    public Map<String, String> checkStatus() {
        Map<String, String> status = new LinkedHashMap<>();

        try {
            jdbcTemplate.execute("SELECT 1");
            status.put("postgresql", "Conectado");
        } catch (Exception e) {
            status.put("postgresql", "Error: " + e.getMessage());
        }

        try {
            String dbName = mongoTemplate.getDb().getName();
            status.put("mongodb", "Conectado - DB: " + dbName);
        } catch (Exception e) {
            status.put("mongodb", "Error: " + e.getMessage());
        }

        status.put("backend_status", "En ejecución");
        return status;
    }
}