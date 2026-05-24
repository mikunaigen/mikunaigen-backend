package com.mikunaigen.backend.service;

import com.mikunaigen.backend.support.WireMockIntegrationSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import software.amazon.awssdk.services.s3.S3Client;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

@SpringBootTest(classes = {BackupService.class, BackupServiceGithubDispatchIntegrationTest.TestConfig.class})
@ActiveProfiles("integration-test")
class BackupServiceGithubDispatchIntegrationTest extends WireMockIntegrationSupport {

    @Autowired
    private BackupService backupService;

    @DynamicPropertySource
    static void wireMockProps(DynamicPropertyRegistry registry) {
        registry.add("app.github.api-base-url", WireMockIntegrationSupport::wireMockBaseUrl);
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/integrationdb");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
    }

    @BeforeEach
    void stubGithubDispatch() {
        WIRE_MOCK.stubFor(post(urlEqualTo("/repos/mikunaigen/mikunaigen/dispatches"))
                .willReturn(aResponse().withStatus(204)));
    }

    @Test
    void generarRespaldo_encolaDispatchB2Simulado() {
        // HU-22: generación de respaldo dispara petición HTTP simulada hacia GitHub/B2
        assertThatCode(() -> backupService.generate("postgresql")).doesNotThrowAnyException();

        WIRE_MOCK.verify(postRequestedFor(urlEqualTo("/repos/mikunaigen/mikunaigen/dispatches"))
                .withRequestBody(containing("trigger-generate")));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        S3Client s3Client() {
            return mock(S3Client.class);
        }
    }
}
