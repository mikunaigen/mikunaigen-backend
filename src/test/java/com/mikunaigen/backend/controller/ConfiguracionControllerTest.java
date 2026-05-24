package com.mikunaigen.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikunaigen.backend.config.JacksonConfig;
import com.mikunaigen.backend.model.sql.ConfiguracionGlobal;
import com.mikunaigen.backend.repository.sql.ConfiguracionGlobalRepository;
import com.mikunaigen.backend.repository.sql.VerificationCodeRepository;
import com.mikunaigen.backend.security.JwtService;
import com.mikunaigen.backend.service.EmailService;
import com.mikunaigen.backend.service.MaintenanceModeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConfiguracionController.class)
@Import(JacksonConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class ConfiguracionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ConfiguracionGlobalRepository configRepo;

    @MockitoBean
    private VerificationCodeRepository codeSqlRepo;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private MaintenanceModeService maintenanceModeService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void enviarVerificacion_nonGmailEmail_rejected() throws Exception {
        // HU-33: correo distinto de @gmail.com es rechazado
        Map<String, String> request = Map.of(
                "emailSmtp", "admin@outlook.com",
                "passwordSmtp", "1234567890123456");

        mockMvc.perform(post("/api/configuracion/enviar-verificacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Debe ser un correo @gmail.com"));
    }

    @Test
    void enviarVerificacion_passwordNot16Chars_rejected() throws Exception {
        // HU-33: contraseña SMTP distinta de 16 caracteres es rechazada
        Map<String, String> request = Map.of(
                "emailSmtp", "admin@gmail.com",
                "passwordSmtp", "short");

        mockMvc.perform(post("/api/configuracion/enviar-verificacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("La contraseña de aplicación de Google debe tener 16 caracteres"));
    }

    @Test
    void guardarPlataforma_validData_savesConfiguration() throws Exception {
        // HU-34: datos válidos guardan configuración de plataforma
        when(configRepo.findById(1)).thenReturn(Optional.of(new ConfiguracionGlobal()));
        when(configRepo.save(any(ConfiguracionGlobal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> request = validPlataformaRequest();
        request.remove("logoBase64");

        mockMvc.perform(post("/api/configuracion/plataforma")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Configuración de la plataforma guardada correctamente."));

        verify(configRepo).save(any(ConfiguracionGlobal.class));
    }

    @Test
    void guardarPlataforma_invalidYapePlinFormat_rejected() throws Exception {
        // HU-34: formato inválido de Yape/Plin es rechazado
        Map<String, Object> request = validPlataformaRequest();
        request.put("numeroYape", "812345678");
        request.put("numeroPlin", "123456789");

        mockMvc.perform(post("/api/configuracion/plataforma")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Yape y Plin deben tener 9 dígitos y empezar en 9."));
    }

    @Test
    void guardarPlataforma_logoOver2Mb_rejected() throws Exception {
        // HU-34: logo mayor a 2 MB es rechazado
        when(configRepo.findById(1)).thenReturn(Optional.of(new ConfiguracionGlobal()));

        byte[] oversizedLogo = new byte[2 * 1024 * 1024 + 1];
        String logoBase64 = Base64.getEncoder().encodeToString(oversizedLogo);

        Map<String, Object> request = validPlataformaRequest();
        request.put("logoBase64", logoBase64);

        mockMvc.perform(post("/api/configuracion/plataforma")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El logo no debe superar 2 MB."));
    }

    private Map<String, Object> validPlataformaRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("nombrePlataforma", "Mikunaigen Test");
        request.put("telefonoContacto", "987654321");
        request.put("numeroYape", "912345678");
        request.put("numeroPlin", "923456789");
        request.put("bancoNombre", "BCP");
        request.put("cuentaBancaria", "1234567890");
        request.put("cci", "98765432101234567890");
        request.put("terminosCondiciones", "Términos y condiciones de prueba.");
        return request;
    }
}
