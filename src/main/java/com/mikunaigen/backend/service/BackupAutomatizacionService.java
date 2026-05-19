package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.sql.ConfiguracionGlobal;
import com.mikunaigen.backend.repository.sql.ConfiguracionGlobalRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class BackupAutomatizacionService {

    private static final ZoneId ZONE = ZoneId.of("America/Lima");

    private final BackupService backupService;
    private final ConfiguracionGlobalRepository configRepository;

    public BackupAutomatizacionService(BackupService backupService, ConfiguracionGlobalRepository configRepository) {
        this.backupService = backupService;
        this.configRepository = configRepository;
    }

    public Map<String, Object> obtenerEstado() {
        ConfiguracionGlobal c = configRepository.findById(1).orElse(new ConfiguracionGlobal());
        Map<String, Object> out = new HashMap<>();
        out.put("programacion", c.getProgramacionBackup() != null ? c.getProgramacionBackup() : "ninguno");
        out.put("proximoBackup", calcularProximo(c.getProgramacionBackup()));
        return out;
    }

    public Map<String, Object> guardarProgramacion(String frecuencia) {
        String norm = switch (frecuencia == null ? "" : frecuencia.toLowerCase()) {
            case "diaria", "daily" -> "diaria";
            case "semanal", "weekly" -> "semanal";
            case "mensual", "monthly" -> "mensual";
            default -> "ninguno";
        };
        ConfiguracionGlobal c = configRepository.findById(1).orElse(new ConfiguracionGlobal());
        c.setId(1);
        c.setProgramacionBackup(norm);
        c.setActualizadoEn(LocalDateTime.now());
        configRepository.save(c);
        return Map.of(
                "message", "Programación actualizada.",
                "programacion", norm,
                "proximoBackup", calcularProximo(norm)
        );
    }

    public void ejecutarAutomaticoSiCorresponde() {
        ConfiguracionGlobal c = configRepository.findById(1).orElse(null);
        if (c == null || c.getProgramacionBackup() == null || "ninguno".equals(c.getProgramacionBackup())) {
            return;
        }
        backupService.generate("postgresql");
    }

    private String calcularProximo(String programacion) {
        if (programacion == null || "ninguno".equals(programacion)) {
            return null;
        }
        LocalDateTime base = LocalDateTime.now(ZONE).plusDays(1).withHour(2).withMinute(0);
        return base.toString();
    }

    public Map<String, Object> getForAdmin() {
        return obtenerEstado();
    }

    public Map<String, Object> saveFromAdmin(Map<String, Object> body) {
        String freq = body != null && body.get("frequency") != null
                ? String.valueOf(body.get("frequency"))
                : body != null && body.get("frecuencia") != null ? String.valueOf(body.get("frecuencia")) : "ninguno";
        return guardarProgramacion(freq);
    }
}
