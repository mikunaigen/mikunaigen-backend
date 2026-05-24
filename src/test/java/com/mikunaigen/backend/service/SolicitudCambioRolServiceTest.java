package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.sql.Role;
import com.mikunaigen.backend.model.sql.SolicitudCambioRol;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.ConfiguracionGlobalRepository;
import com.mikunaigen.backend.repository.sql.RoleRepository;
import com.mikunaigen.backend.repository.sql.SolicitudCambioRolRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitudCambioRolServiceTest {

    private static final String JUSTIFICACION_VALIDA =
            "Solicito el plan emprendedor para ampliar el uso de inferencias en mi negocio.";

    @Mock
    private SolicitudCambioRolRepository solicitudRepo;

    @Mock
    private UserRepository userRepo;

    @Mock
    private RoleRepository roleRepo;

    @Mock
    private ConfiguracionGlobalRepository configRepo;

    @Mock
    private EmailService emailService;

    @Mock
    private SolicitudPlanPushService planPushService;

    private SolicitudCambioRolService service;

    @BeforeEach
    void setUp() {
        service = new SolicitudCambioRolService(
                solicitudRepo, userRepo, roleRepo, configRepo, emailService, planPushService
        );
    }

    @Test
    void crearSolicitud_comprobanteValido_quedaPendiente() throws IOException {
        // HU-29
        User user = usuarioEstudiante();
        MultipartFile comprobante = mockComprobante("comprobante.png", "image/png", 1024, new byte[]{1, 2, 3});
        Role rolEmprendedor = rol("emprendedor", 2);

        when(solicitudRepo.findFirstByUsuarioIdAndEstadoIgnoreCase(user.getId(), "pendiente"))
                .thenReturn(Optional.empty());
        when(roleRepo.findByNombre("emprendedor")).thenReturn(Optional.of(rolEmprendedor));

        ResponseEntity<?> respuesta = service.crearSolicitud(
                user, "emprendedor", JUSTIFICACION_VALIDA, comprobante);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        ArgumentCaptor<SolicitudCambioRol> captor = ArgumentCaptor.forClass(SolicitudCambioRol.class);
        verify(solicitudRepo).save(captor.capture());
        SolicitudCambioRol guardada = captor.getValue();
        assertThat(guardada.getEstado()).isEqualToIgnoringCase("pendiente");
        assertThat(guardada.getUsuarioId()).isEqualTo(user.getId());
        assertThat(guardada.getComprobantePagoBytea()).isNotEmpty();
    }

    @Test
    void crearSolicitud_rechazaFormatoComprobanteInvalido() {
        // HU-29
        User user = usuarioEstudiante();
        MultipartFile comprobante = mock(MultipartFile.class);
        when(comprobante.isEmpty()).thenReturn(false);
        when(comprobante.getOriginalFilename()).thenReturn("comprobante.pdf");
        when(comprobante.getContentType()).thenReturn("application/pdf");

        when(solicitudRepo.findFirstByUsuarioIdAndEstadoIgnoreCase(user.getId(), "pendiente"))
                .thenReturn(Optional.empty());

        ResponseEntity<?> respuesta = service.crearSolicitud(
                user, "emprendedor", JUSTIFICACION_VALIDA, comprobante);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(respuesta.getBody()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) respuesta.getBody()).get("message"))
                .asString()
                .containsIgnoringCase("PNG");
    }

    @Test
    void crearSolicitud_rechazaComprobanteMayorA2Mb() {
        // HU-29
        User user = usuarioEstudiante();
        long tamano = 2L * 1024 * 1024 + 1;
        MultipartFile comprobante = mock(MultipartFile.class);
        when(comprobante.isEmpty()).thenReturn(false);
        when(comprobante.getOriginalFilename()).thenReturn("comprobante.png");
        when(comprobante.getContentType()).thenReturn("image/png");
        when(comprobante.getSize()).thenReturn(tamano);

        when(solicitudRepo.findFirstByUsuarioIdAndEstadoIgnoreCase(user.getId(), "pendiente"))
                .thenReturn(Optional.empty());

        ResponseEntity<?> respuesta = service.crearSolicitud(
                user, "emprendedor", JUSTIFICACION_VALIDA, comprobante);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(((Map<?, ?>) respuesta.getBody()).get("message"))
                .asString()
                .containsIgnoringCase("2 MB");
    }

    private static User usuarioEstudiante() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("estudiante@example.com");
        user.setRole(rol("estudiante", 1));
        return user;
    }

    private static Role rol(String nombre, int id) {
        Role role = new Role();
        role.setId(id);
        role.setNombre(nombre);
        return role;
    }

    private static MultipartFile mockComprobante(
            String filename, String contentType, long size, byte[] content
    ) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getContentType()).thenReturn(contentType);
        when(file.getSize()).thenReturn(size);
        try {
            when(file.getBytes()).thenReturn(content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return file;
    }
}
