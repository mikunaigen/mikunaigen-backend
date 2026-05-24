package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.sql.Role;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.model.sql.VerificationCode;
import com.mikunaigen.backend.repository.sql.RoleRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import com.mikunaigen.backend.repository.sql.VerificationCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistroTelegramServiceTest {

    @Mock
    private UserRepository userRepo;

    @Mock
    private RoleRepository roleRepo;

    @Mock
    private VerificationCodeRepository codeRepo;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RegistroActivacionPushService pushService;

    @Mock
    private PreferenciasUsuarioService preferenciasUsuarioService;

    @InjectMocks
    private RegistroTelegramService service;

    private static final String VALID_PASSWORD = "Password1@";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "botUsername", "mikunaigen_bot");
    }

    @Test
    void registrarPendiente_validRegistration_savesPendienteUserWithEstudianteRoleAndActivationCode() {
        // HU-01: registro válido crea usuario pendiente con rol estudiante
        Map<String, String> data = validRegistrationData();

        Role estudiante = new Role();
        estudiante.setNombre("estudiante");
        UUID userId = UUID.randomUUID();
        User savedUser = new User();
        savedUser.setId(userId);
        savedUser.setEmail(data.get("email"));
        savedUser.setRole(estudiante);
        savedUser.setEstado("pendiente");

        when(userRepo.findByEmailIgnoreCase(data.get("email"))).thenReturn(Optional.empty());
        when(userRepo.count()).thenReturn(1L);
        when(userRepo.existsByEmailIgnoreCase(data.get("email"))).thenReturn(false);
        when(userRepo.existsByTelefono(data.get("phone"))).thenReturn(false);
        when(userRepo.findAll()).thenReturn(java.util.List.of());
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn("encoded-password");
        when(roleRepo.findByNombre("estudiante")).thenReturn(Optional.of(estudiante));
        when(userRepo.save(any(User.class))).thenReturn(savedUser);
        when(codeRepo.findFirstByReferenciaAndPropositoAndUsadoOrderByFechaExpiracionDesc(any(), any(), any(Boolean.class)))
                .thenReturn(Optional.empty());
        when(codeRepo.save(any(VerificationCode.class))).thenAnswer(invocation -> {
            VerificationCode code = invocation.getArgument(0);
            code.setCodigo("123456");
            return code;
        });

        ResponseEntity<?> response = service.registrarPendiente(data);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body)
                .containsEntry("userId", userId.toString())
                .containsEntry("prefijoCodigo", RegistroTelegramService.CODIGO_PREFIJO);
        assertThat(body.get("codigoActivacion").toString()).startsWith("MIKUNA-VALTEL-");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(userCaptor.capture());
        User captured = userCaptor.getValue();
        assertThat(captured.getEstado()).isEqualTo("pendiente");
        assertThat(captured.getRole().getName()).isEqualTo("estudiante");
        assertThat(captured.getPassword()).isEqualTo("encoded-password");
        assertThat(captured.isAceptoTerminos()).isTrue();
        assertThat(captured.isAceptoDescargo()).isTrue();
        verify(passwordEncoder).encode(VALID_PASSWORD);
        verify(preferenciasUsuarioService).asegurarPreferenciasIniciales(userId);
    }

    @Test
    void registrarPendiente_duplicateActiveEmail_rejected() {
        // HU-01: correo activo duplicado es rechazado
        Map<String, String> data = validRegistrationData();
        User existing = new User();
        existing.setEmail(data.get("email"));
        existing.setEstado("activo");

        when(userRepo.findByEmailIgnoreCase(data.get("email"))).thenReturn(Optional.of(existing));

        ResponseEntity<?> response = service.registrarPendiente(data);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body.get("message")).contains("Ya existe un usuario con ese correo electrónico");
        verify(userRepo, never()).save(any(User.class));
    }

    @Test
    void registrarPendiente_weakPassword_rejected() {
        // HU-01: contraseña débil es rechazada
        Map<String, String> data = validRegistrationData();
        data.put("password", "weak");

        ResponseEntity<?> response = service.registrarPendiente(data);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body.get("message")).contains("La contraseña no cumple los requisitos de seguridad");
        verify(userRepo, never()).save(any(User.class));
    }

    @Test
    void registrarPendiente_missingAceptoTerminos_rejected() {
        // HU-28/29: falta aceptoTerminos
        Map<String, String> data = validRegistrationData();
        data.put("aceptoTerminos", "false");

        ResponseEntity<?> response = service.registrarPendiente(data);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body.get("message")).contains("Debes aceptar los términos y condiciones de uso");
        verify(userRepo, never()).save(any(User.class));
    }

    @Test
    void registrarPendiente_missingAceptoDescargo_rejected() {
        // HU-28/29: falta aceptoDescargo
        Map<String, String> data = validRegistrationData();
        data.put("aceptoDescargo", "false");

        ResponseEntity<?> response = service.registrarPendiente(data);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body.get("message")).contains("Debes aceptar el descargo de responsabilidad");
        verify(userRepo, never()).save(any(User.class));
    }

    private Map<String, String> validRegistrationData() {
        Map<String, String> data = new HashMap<>();
        data.put("fullName", "Juan Pérez");
        data.put("dni", "12345678");
        data.put("phone", "987654321");
        data.put("email", "juan@gmail.com");
        data.put("password", VALID_PASSWORD);
        data.put("aceptoTerminos", "true");
        data.put("aceptoDescargo", "true");
        return data;
    }
}
