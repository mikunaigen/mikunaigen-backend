package com.mikunaigen.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikunaigen.backend.model.sql.IpLoginAttempt;
import com.mikunaigen.backend.model.sql.Role;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.model.sql.VerificationCode;
import com.mikunaigen.backend.config.JacksonConfig;
import com.mikunaigen.backend.repository.sql.*;
import com.mikunaigen.backend.security.JwtService;
import com.mikunaigen.backend.service.EmailService;
import com.mikunaigen.backend.service.MaintenanceModeService;
import com.mikunaigen.backend.service.RegistroTelegramService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(JacksonConfig.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserRepository userRepo;

    @MockitoBean
    private RoleRepository roleRepo;

    @MockitoBean
    private VerificationCodeRepository codeRepo;

    @MockitoBean
    private IpLoginAttemptRepository ipLoginAttemptRepo;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private ConfiguracionGlobalRepository configRepo;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RegistroTelegramService registroTelegramService;

    @MockitoBean
    private MaintenanceModeService maintenanceModeService;

    @Test
    void loginValidCredentialsReturnsToken() throws Exception {
        // HU-02: login valid credentials returns token
        UUID userId = UUID.randomUUID();
        Role role = new Role();
        role.setNombre("estudiante");

        User user = new User();
        user.setId(userId);
        user.setEmail("user@test.com");
        user.setNombres("Ana");
        user.setApellidos("Perez");
        user.setEstado("activo");
        user.setRole(role);
        user.setFirstLogin(false);
        user.setPassword("encoded");

        when(userRepo.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret1!", user.getPassword())).thenReturn(true);
        when(jwtService.generateToken("user@test.com", userId.toString(), "estudiante")).thenReturn("jwt-token");
        when(ipLoginAttemptRepo.findByIpAddress(anyString())).thenReturn(Optional.empty());
        when(ipLoginAttemptRepo.save(any(IpLoginAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "user@test.com",
                                "password", "Secret1!"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.email").value("user@test.com"));
    }

    @Test
    void loginWrongPasswordReturns401() throws Exception {
        // HU-02: login wrong password 401
        Role role = new Role();
        role.setNombre("estudiante");

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@test.com");
        user.setEstado("activo");
        user.setRole(role);
        user.setFirstLogin(false);
        wantsPassword(user);

        IpLoginAttempt intento = new IpLoginAttempt();
        intento.setIpAddress("127.0.0.1");
        intento.setFailedAttempts(0);

        when(userRepo.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);
        when(ipLoginAttemptRepo.findByIpAddress(anyString())).thenReturn(Optional.of(intento));
        when(ipLoginAttemptRepo.save(any(IpLoginAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "user@test.com",
                                "password", "wrong"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Contraseña incorrecta."));
    }

    @Test
    void enviarRecuperacionUnknownEmailReturns400() throws Exception {
        // HU-03: enviarRecuperacion unknown email 400
        when(userRepo.existsByEmail("unknown@test.com")).thenReturn(false);

        mockMvc.perform(post("/api/auth/enviar-codigo-recuperacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "unknown@test.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Correo no registrado."));
    }

    @Test
    void resetPasswordExpiredCodeReturns400() throws Exception {
        // HU-03: resetPassword expired code 400
        VerificationCode vCode = new VerificationCode();
        vCode.setEmail("user@test.com");
        vCode.setCode("123456");
        vCode.setExpirationTime(LocalDateTime.now().minusMinutes(5));

        when(codeRepo.findFirstByEmailAndUsedOrderByExpirationTimeDesc("user@test.com", false))
                .thenReturn(Optional.of(vCode));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "user@test.com",
                                "codigo", "123456",
                                "newPassword", "NewPass1!"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El código ha expirado."));
    }

    @Test
    void threeFailedAttemptsBlocksIp() throws Exception {
        // HU-04: three failed attempts blocks IP (LOCKED 423)
        Role role = new Role();
        role.setNombre("estudiante");

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@test.com");
        user.setEstado("activo");
        user.setRole(role);
        user.setFirstLogin(false);
        wantsPassword(user);

        IpLoginAttempt intento = new IpLoginAttempt();
        intento.setIpAddress("127.0.0.1");
        intento.setFailedAttempts(2);

        when(userRepo.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);
        when(ipLoginAttemptRepo.findByIpAddress(anyString())).thenReturn(Optional.of(intento));
        when(ipLoginAttemptRepo.save(any(IpLoginAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "user@test.com",
                                "password", "wrong"
                        ))))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.blocked").value(true))
                .andExpect(jsonPath("$.message").value("Tu IP ha sido restringida por 1 hora por varios intentos fallidos."));
    }

    @Test
    void ipStatusAfterBlockExpiryAllowsAccess() throws Exception {
        // HU-04: ip-status after block expiry allows access
        IpLoginAttempt intento = new IpLoginAttempt();
        intento.setIpAddress("127.0.0.1");
        intento.setFailedAttempts(3);
        intento.setBlockedUntil(LocalDateTime.now().minusMinutes(1));

        when(ipLoginAttemptRepo.findByIpAddress(anyString())).thenReturn(Optional.of(intento));
        when(ipLoginAttemptRepo.save(any(IpLoginAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(get("/api/auth/ip-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(false))
                .andExpect(jsonPath("$.remainingSeconds").value(0));

        verify(ipLoginAttemptRepo).save(argThat(saved ->
                saved.getBlockedUntil() == null && saved.getFailedAttempts() == 0));
    }

    @Test
    void updateDarkModeSavesPreference() throws Exception {
        // HU-26: updateDarkMode saves preference
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@test.com");
        user.setDarkMode(false);

        when(userRepo.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(patch("/api/auth/dark-mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "user@test.com",
                                "darkMode", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.darkMode").value(true));

        assertThat(user.isDarkMode()).isTrue();
        verify(userRepo).save(user);
    }

    private static void wantsPassword(User user) {
        user.setPassword("encoded");
    }
}
