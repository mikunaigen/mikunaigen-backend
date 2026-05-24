package com.mikunaigen.backend.service;

import com.mikunaigen.backend.support.WireMockIntegrationSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ContextoInteligenciaService.class)
@ActiveProfiles("integration-test")
class ContextoInteligenciaServiceIntegrationTest extends WireMockIntegrationSupport {

    @Autowired
    private ContextoInteligenciaService contextoInteligenciaService;

    @DynamicPropertySource
    static void wireMockProps(DynamicPropertyRegistry registry) {
        registry.add("app.open-meteo.url",
                () -> wireMockBaseUrl() + "/v1/forecast?latitude=-12&longitude=-75&current_weather=true");
    }

    @BeforeEach
    void stubOpenMeteo() {
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/v1/forecast"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"current_weather":{"temperature":18.5,"weathercode":0}}
                                """)));
    }

    @Test
    void consultaClimaExterno_retornaTemperaturaSimulada() {
        // HU-22: consulta HTTP externa simulada con WireMock devuelve contexto parseado
        ContextoInteligenciaService.ContextoInteligencia ctx = contextoInteligenciaService.contextoActual();

        assertThat(ctx.temp()).isEqualTo(18.5);
        assertThat(ctx.condition()).isEqualTo("SOLEADO");
    }
}
