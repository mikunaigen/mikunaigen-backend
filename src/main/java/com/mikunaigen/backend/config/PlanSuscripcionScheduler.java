package com.mikunaigen.backend.config;

import com.mikunaigen.backend.service.SolicitudCambioRolService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PlanSuscripcionScheduler {

    private final SolicitudCambioRolService solicitudCambioRolService;

    public PlanSuscripcionScheduler(SolicitudCambioRolService solicitudCambioRolService) {
        this.solicitudCambioRolService = solicitudCambioRolService;
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void ejecutarVencimientosYRecordatorios() {
        solicitudCambioRolService.procesarVencimientosYRecordatorios();
    }
}
