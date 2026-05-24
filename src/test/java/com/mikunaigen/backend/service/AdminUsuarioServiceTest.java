package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.sql.Role;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUsuarioServiceTest {

    @Mock
    private UserRepository userRepo;

    @Mock
    private RoleRepository roleRepo;

    @Mock
    private SolicitudCambioRolRepository solicitudRepo;

    @Mock
    private ConfiguracionGlobalRepository configRepo;

    @Mock
    private EmailService emailService;

    @Mock
    private CuentaUsuarioPushService cuentaPushService;

    private AdminUsuarioService service;

    @BeforeEach
    void setUp() {
        service = new AdminUsuarioService(
                userRepo, roleRepo, solicitudRepo, configRepo, emailService, cuentaPushService
        );
    }

    @Test
    void listarUsuarios_filtraPorRol() {
        // HU-07
        User estudiante = usuarioGestionable("estudiante", "ana@example.com");
        User emprendedor = usuarioGestionable("emprendedor", "bob@example.com");
        when(userRepo.findAll()).thenReturn(List.of(estudiante, emprendedor));
        when(solicitudRepo.findByEstadoIgnoreCaseOrderByFechaSolicitudDesc("pendiente"))
                .thenReturn(List.of());

        List<Map<String, Object>> resultado = service.listarUsuarios(null, "emprendedor", null);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).get("rol")).isEqualTo("emprendedor");
    }

    @Test
    void desactivarCuenta_estableceSuspendido() {
        // HU-07
        UUID userId = UUID.randomUUID();
        User user = usuarioGestionable("estudiante", "cuenta@example.com");
        user.setId(userId);
        user.setEstado("activo");
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        ResponseEntity<?> respuesta = service.desactivarCuenta(userId);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());
        User guardado = captor.getValue();
        assertThat(guardado.getEstado()).isEqualToIgnoringCase("suspendido");
        assertThat(guardado.isDeleted()).isTrue();
    }

    private static User usuarioGestionable(String rolNombre, String email) {
        Role role = new Role();
        role.setId(1);
        role.setNombre(rolNombre);
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(role);
        user.setEmail(email);
        user.setFullName("Usuario Test");
        user.setEstado("activo");
        return user;
    }
}
