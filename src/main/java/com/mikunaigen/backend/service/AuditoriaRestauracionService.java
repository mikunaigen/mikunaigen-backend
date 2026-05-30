package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.sql.AuditoriaRestauracion;
import com.mikunaigen.backend.repository.sql.AuditoriaRestauracionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuditoriaRestauracionService {

    public static final String ESTADO_EN_PROCESO = "en_proceso";
    public static final String ESTADO_EXITOSA = "exitosa";
    public static final String ESTADO_FALLIDA = "fallida";

    private final AuditoriaRestauracionRepository repository;

    public AuditoriaRestauracionService(AuditoriaRestauracionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Integer iniciarRestauracion(UUID administradorId, String nombreArchivo) {
        AuditoriaRestauracion auditoria = new AuditoriaRestauracion();
        auditoria.setAdministradorId(administradorId);
        auditoria.setBackupNombreArchivo(nombreArchivo != null ? nombreArchivo : "desconocido");
        auditoria.setEstado(ESTADO_EN_PROCESO);
        auditoria.setFechaInicio(LocalDateTime.now());
        return repository.save(auditoria).getId();
    }

    @Transactional
    public void marcarExitosa(Integer auditoriaId) {
        if (auditoriaId == null) {
            marcarUltimaEnProcesoComo(ESTADO_EXITOSA);
            return;
        }
        repository.findById(auditoriaId).ifPresent(a -> {
            a.setEstado(ESTADO_EXITOSA);
            a.setFechaFin(LocalDateTime.now());
            repository.save(a);
        });
    }

    @Transactional
    public void marcarFallida(Integer auditoriaId) {
        if (auditoriaId == null) {
            marcarUltimaEnProcesoComo(ESTADO_FALLIDA);
            return;
        }
        repository.findById(auditoriaId).ifPresent(a -> {
            a.setEstado(ESTADO_FALLIDA);
            a.setFechaFin(LocalDateTime.now());
            repository.save(a);
        });
    }

    private void marcarUltimaEnProcesoComo(String estado) {
        repository.findFirstByEstadoOrderByFechaInicioDesc(ESTADO_EN_PROCESO).ifPresent(a -> {
            a.setEstado(estado);
            a.setFechaFin(LocalDateTime.now());
            repository.save(a);
        });
    }
}
