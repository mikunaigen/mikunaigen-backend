package com.mikunaigen.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ObjetivoNutricionalServiceTest {

    private ObjetivoNutricionalService service;

    @BeforeEach
    void setUp() {
        service = new ObjetivoNutricionalService();
    }

    @Test
    void validar_withAll21Fields_returnsValidoTrue() {
        // HU-08: 21 campos válidos retornan valido true
        Map<String, Object> body = validObjetivoBody();

        Map<String, Object> result = service.validar(body);

        assertThat(result)
                .containsEntry("valido", true)
                .containsEntry("message", "Objetivo nutricional válido.");
        @SuppressWarnings("unchecked")
        Map<String, Object> valores = (Map<String, Object>) result.get("valores");
        assertThat(valores).hasSize(21);
        ObjetivoNutricionalService.CAMPOS.forEach(campo ->
                assertThat(valores).containsKey(campo.get("key")));
    }

    @Test
    void perfilesEjemplo_returnsThreeProfilesWithValues() {
        // HU-08: perfilesEjemplo retorna 3 perfiles con valores
        List<Map<String, Object>> perfiles = service.perfilesEjemplo();

        assertThat(perfiles).hasSize(3);
        assertThat(perfiles)
                .extracting(p -> p.get("id"))
                .containsExactly("carne-vegetal-andina", "batido-inmunologico", "galleta-anemia");
        assertThat(perfiles)
                .extracting(p -> p.get("nombre"))
                .containsExactly(
                        "Carne Vegetal Andina",
                        "Batido Energético Inmunológico",
                        "Galleta Escolar contra la Anemia");
        perfiles.forEach(perfil -> {
            assertThat(perfil.get("icon")).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> valores = (Map<String, Object>) perfil.get("valores");
            assertThat(valores).isNotEmpty();
            assertThat(valores).containsKeys("energia_kcal", "proteinas_g", "costo_kg_soles");
        });
    }

    private Map<String, Object> validObjetivoBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("energia_kcal", 220);
        body.put("agua_g", 55);
        body.put("proteinas_g", 18);
        body.put("grasa_total_g", 8);
        body.put("carbohidratos_disponibles_g", 22);
        body.put("fibra_g", 6);
        body.put("cenizas_g", 2);
        body.put("calcio_mg", 80);
        body.put("fosforo_mg", 200);
        body.put("zinc_mg", 3);
        body.put("hierro_mg", 4.5);
        body.put("beta_caroteno_ug", 150);
        body.put("vitamina_a_ug", 25);
        body.put("tiamina_mg", 0.15);
        body.put("riboflavina_mg", 0.12);
        body.put("niacina_mg", 2.5);
        body.put("vitamina_c_mg", 2);
        body.put("acido_folico_ug", 45);
        body.put("sodio_mg", 380);
        body.put("potasio_mg", 450);
        body.put("costo_kg_soles", 18);
        return body;
    }
}
