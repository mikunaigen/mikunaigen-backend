package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.sql.AuditoriaAcceso;
import com.mikunaigen.backend.repository.sql.AuditoriaAccesoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditoriaAccesoService {

    private final AuditoriaAccesoRepository repository;

    public AuditoriaAccesoService(AuditoriaAccesoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void registrarAccesoExitoso(UUID usuarioId, String direccionIp, String userAgent) {
        if (usuarioId == null || direccionIp == null || direccionIp.isBlank()) {
            return;
        }
        AuditoriaAcceso acceso = new AuditoriaAcceso();
        acceso.setUsuarioId(usuarioId);
        acceso.setDireccionIp(truncar(direccionIp, 45));
        acceso.setUserAgent(truncar(userAgent, 2000));
        repository.save(acceso);
    }

    private String truncar(String valor, int max) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= max ? valor : valor.substring(0, max);
    }
}
