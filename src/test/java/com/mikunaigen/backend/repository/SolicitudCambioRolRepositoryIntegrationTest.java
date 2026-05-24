package com.mikunaigen.backend.repository;

import com.mikunaigen.backend.model.sql.SolicitudCambioRol;
import com.mikunaigen.backend.repository.sql.SolicitudCambioRolRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SolicitudCambioRolRepositoryIntegrationTest {

    @Autowired
    private SolicitudCambioRolRepository solicitudRepository;

    @Test
    void solicitudValida_quedaEnEstadoPendiente() {
        // HU-29: solicitud con comprobante válido queda pendiente
        SolicitudCambioRol solicitud = new SolicitudCambioRol();
        solicitud.setUsuarioId(UUID.randomUUID());
        solicitud.setRolSolicitadoId(2);
        solicitud.setJustificacion("Necesito acceder a funcionalidades de nutricionista para mi emprendimiento.");
        solicitud.setComprobantePagoBytea(new byte[] { (byte) 0xFF, (byte) 0xD8, 1, 2, 3 });
        solicitud.setEstado("pendiente");
        solicitud.setFechaSolicitud(LocalDateTime.now());

        SolicitudCambioRol guardada = solicitudRepository.save(solicitud);

        Optional<SolicitudCambioRol> pendiente = solicitudRepository
                .findFirstByUsuarioIdAndEstadoIgnoreCase(guardada.getUsuarioId(), "pendiente");

        assertThat(pendiente).isPresent();
        assertThat(pendiente.get().getEstado()).isEqualToIgnoringCase("pendiente");
        assertThat(pendiente.get().getComprobantePagoBytea()).isNotEmpty();
    }
}
