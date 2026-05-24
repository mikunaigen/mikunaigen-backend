package com.mikunaigen.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikunaigen.backend.config.JacksonConfig;
import com.mikunaigen.backend.model.sql.Role;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.ConfiguracionGlobalRepository;
import com.mikunaigen.backend.repository.sql.RoleRepository;
import com.mikunaigen.backend.repository.sql.SolicitudCambioRolRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import com.mikunaigen.backend.repository.sql.VerificationCodeRepository;
import com.mikunaigen.backend.security.JwtService;
import com.mikunaigen.backend.service.EmailService;
import com.mikunaigen.backend.service.MaintenanceModeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PerfilController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(JacksonConfig.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class PerfilControllerTest {

    private static final String EMAIL = "user@test.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private ConfiguracionGlobalRepository configuracionGlobalRepository;

    @MockitoBean
    private SolicitudCambioRolRepository solicitudCambioRolRepository;

    @MockitoBean
    private RoleRepository roleRepository;

    @MockitoBean
    private VerificationCodeRepository verificationCodeRepository;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private MaintenanceModeService maintenanceModeService;

    @MockitoBean
    private JwtService jwtService;

    @BeforeEach
    void setUpSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(EMAIL, null)
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validNameUpdateReturnsSuccessMessage() throws Exception {
        // HU-05: valid name update returns "Datos Actualizados Correctamente"
        User user = authenticatedUser("Maria", "Lopez");

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(solicitudCambioRolRepository.findFirstByUsuarioIdAndEstadoIgnoreCase(any(), any()))
                .thenReturn(Optional.empty());

        mockMvc.perform(put("/api/perfil/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nombres", "Carlos",
                                "apellidos", "Garcia"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Datos Actualizados Correctamente"))
                .andExpect(jsonPath("$.nombres").value("Carlos"))
                .andExpect(jsonPath("$.apellidos").value("Garcia"));

        verify(userRepository).save(user);
    }

    @Test
    void namesWithNumbersAreRejected() throws Exception {
        // HU-05: names with numbers rejected
        User user = authenticatedUser("Maria", "Lopez");

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        mockMvc.perform(put("/api/perfil/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nombres", "Juan123",
                                "apellidos", "Perez"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Los nombres no pueden contener números."));
    }

    private static User authenticatedUser(String nombres, String apellidos) {
        Role role = new Role();
        role.setNombre("estudiante");

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(EMAIL);
        user.setNombres(nombres);
        user.setApellidos(apellidos);
        user.setEstado("activo");
        user.setRole(role);
        return user;
    }
}
