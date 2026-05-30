package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.sql.AuditoriaEntrenamientoIa;
import com.mikunaigen.backend.model.sql.ConfiguracionIa;
import com.mikunaigen.backend.repository.sql.AuditoriaEntrenamientoIaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditoriaEntrenamientoIaService {

    private final AuditoriaEntrenamientoIaRepository repository;

    public AuditoriaEntrenamientoIaService(AuditoriaEntrenamientoIaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void registrarDespliegueProduccion(
            UUID administradorId,
            ConfiguracionIa configuracion,
            Map<String, Object> cuerpoWebhook
    ) {
        if (administradorId == null || configuracion == null) {
            return;
        }
        AuditoriaEntrenamientoIa auditoria = new AuditoriaEntrenamientoIa();
        auditoria.setAdministradorId(administradorId);
        auditoria.setEpocasProcesadas(valorEntero(configuracion.getEntrenamientoEpoca(), 0));
        auditoria.setErrorEntrenamiento(decimal(configuracion.getEntrenamientoErrorTrain(), cuerpoWebhook.get("trainError")));
        auditoria.setErrorValidacion(decimal(configuracion.getEntrenamientoErrorVal(), cuerpoWebhook.get("valError")));
        auditoria.setSuperoUmbralOverfitting(false);
        auditoria.setDesplegadoProduccion(true);
        auditoria.setFechaInicio(configuracion.getEntrenamientoIniciadoEn() != null
                ? configuracion.getEntrenamientoIniciadoEn()
                : LocalDateTime.now());
        auditoria.setFechaFin(LocalDateTime.now());
        repository.save(auditoria);
    }

    private BigDecimal decimal(BigDecimal preferido, Object alterno) {
        if (preferido != null) {
            return preferido;
        }
        if (alterno == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(alterno));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private int valorEntero(Integer valor, int defecto) {
        return valor != null && valor > 0 ? valor : defecto;
    }
}
