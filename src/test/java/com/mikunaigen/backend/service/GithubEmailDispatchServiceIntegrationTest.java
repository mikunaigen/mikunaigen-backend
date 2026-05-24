package com.mikunaigen.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.mikunaigen.backend.config.JacksonConfig;
import com.mikunaigen.backend.support.WireMockIntegrationSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest(classes = {GithubEmailDispatchService.class, JacksonConfig.class})
@ActiveProfiles("integration-test")
@TestPropertySource(properties = {
        "app.github.token=test-github-token",
        "app.github.owner=mikunaigen",
        "app.github.repo=mikunaigen",
        "app.email.dispatch.key-hex=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "app.email.webhook.secret=test",
        "app.public.api.base-url=http://localhost:8080"
})
class GithubEmailDispatchServiceIntegrationTest extends WireMockIntegrationSupport {

    @Autowired
    private GithubEmailDispatchService githubEmailDispatchService;

    @DynamicPropertySource
    static void wireMockProps(DynamicPropertyRegistry registry) {
        registry.add("app.github.api-base-url", WireMockIntegrationSupport::wireMockBaseUrl);
    }

    @BeforeEach
    void stubGithubDispatch() {
        WIRE_MOCK.stubFor(post(urlEqualTo("/repos/mikunaigen/mikunaigen/dispatches"))
                .willReturn(aResponse().withStatus(204)));
    }

    @Test
    void enviarCorreoSmtp_encolaDispatchEnGithub() {
        // HU-33: envío SMTP encola petición HTTP real hacia servicio externo simulado
        assertThatCode(() -> githubEmailDispatchService.dispatchPlainEmail(
                "destino@gmail.com",
                "admin@gmail.com",
                "1234567890123456",
                "Código SMTP",
                "Cuerpo de prueba",
                null
        )).doesNotThrowAnyException();

        WIRE_MOCK.verify(postRequestedFor(urlEqualTo("/repos/mikunaigen/mikunaigen/dispatches"))
                .withHeader("Authorization", equalTo("Bearer test-github-token")));
    }
}
