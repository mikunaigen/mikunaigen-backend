package com.mikunaigen.backend.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ObjetivoNutricionalService {

    public static final List<Map<String, String>> CAMPOS = List.of(
            campo("energia_kcal", "Energía", "kcal"),
            campo("agua_g", "Agua", "g"),
            campo("proteinas_g", "Proteínas", "g"),
            campo("grasa_total_g", "Grasa total", "g"),
            campo("carbohidratos_disponibles_g", "Carbohidratos disponibles", "g"),
            campo("fibra_g", "Fibra dietaria", "g"),
            campo("cenizas_g", "Cenizas", "g"),
            campo("calcio_mg", "Calcio", "mg"),
            campo("fosforo_mg", "Fósforo", "mg"),
            campo("zinc_mg", "Zinc", "mg"),
            campo("hierro_mg", "Hierro", "mg"),
            campo("beta_caroteno_ug", "Beta-caroteno", "µg"),
            campo("vitamina_a_ug", "Vitamina A", "µg"),
            campo("tiamina_mg", "Tiamina", "mg"),
            campo("riboflavina_mg", "Riboflavina", "mg"),
            campo("niacina_mg", "Niacina", "mg"),
            campo("vitamina_c_mg", "Vitamina C", "mg"),
            campo("acido_folico_ug", "Ácido fólico", "µg"),
            campo("sodio_mg", "Sodio", "mg"),
            campo("potasio_mg", "Potasio", "mg"),
            campo("costo_kg_soles", "Costo máximo por kg", "S/")
    );

    public Map<String, Object> validar(Map<String, Object> body) {
        Map<String, String> errores = new LinkedHashMap<>();
        Map<String, Object> valores = new LinkedHashMap<>();

        for (Map<String, String> def : CAMPOS) {
            String key = def.get("key");
            String label = def.get("label");
            Object raw = body != null ? body.get(key) : null;
            if (raw == null || String.valueOf(raw).trim().isEmpty()) {
                errores.put(key, label + ": el campo no puede estar en blanco.");
                continue;
            }
            try {
                BigDecimal n = new BigDecimal(String.valueOf(raw).trim().replace(",", "."));
                if (n.compareTo(BigDecimal.ZERO) < 0) {
                    errores.put(key, label + ": debe ser un valor numérico no negativo.");
                } else {
                    valores.put(key, n);
                }
            } catch (NumberFormatException e) {
                errores.put(key, label + ": debe ser un valor numérico válido.");
            }
        }

        if (!errores.isEmpty()) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("valido", false);
            resp.put("errores", errores);
            resp.put("message", "Corrige los campos indicados.");
            return resp;
        }

        return Map.of(
                "valido", true,
                "message", "Objetivo nutricional válido.",
                "valores", valores
        );
    }

    public List<Map<String, Object>> perfilesEjemplo() {
        return List.of(
                perfil("carne-vegetal-andina", "Carne Vegetal Andina", "heroFire", valoresCarne()),
                perfil("batido-inmunologico", "Batido Energético Inmunológico", "heroBolt", valoresBatido()),
                perfil("galleta-anemia", "Galleta Escolar contra la Anemia", "heroCake", valoresGalleta())
        );
    }

    private Map<String, Object> perfil(String id, String nombre, String icono, Map<String, Object> valores) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("nombre", nombre);
        m.put("icon", icono);
        m.put("valores", valores);
        return m;
    }

    private Map<String, Object> valoresCarne() {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("energia_kcal", 220);
        v.put("agua_g", 55);
        v.put("proteinas_g", 18);
        v.put("grasa_total_g", 8);
        v.put("carbohidratos_disponibles_g", 22);
        v.put("fibra_g", 6);
        v.put("cenizas_g", 2);
        v.put("calcio_mg", 80);
        v.put("fosforo_mg", 200);
        v.put("zinc_mg", 3);
        v.put("hierro_mg", 4.5);
        v.put("beta_caroteno_ug", 150);
        v.put("vitamina_a_ug", 25);
        v.put("tiamina_mg", 0.15);
        v.put("riboflavina_mg", 0.12);
        v.put("niacina_mg", 2.5);
        v.put("vitamina_c_mg", 2);
        v.put("acido_folico_ug", 45);
        v.put("sodio_mg", 380);
        v.put("potasio_mg", 450);
        v.put("costo_kg_soles", 18);
        return v;
    }

    private Map<String, Object> valoresBatido() {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("energia_kcal", 95);
        v.put("agua_g", 78);
        v.put("proteinas_g", 4);
        v.put("grasa_total_g", 2.5);
        v.put("carbohidratos_disponibles_g", 16);
        v.put("fibra_g", 2);
        v.put("cenizas_g", 0.5);
        v.put("calcio_mg", 120);
        v.put("fosforo_mg", 90);
        v.put("zinc_mg", 1.2);
        v.put("hierro_mg", 1);
        v.put("beta_caroteno_ug", 800);
        v.put("vitamina_a_ug", 120);
        v.put("tiamina_mg", 0.08);
        v.put("riboflavina_mg", 0.1);
        v.put("niacina_mg", 0.8);
        v.put("vitamina_c_mg", 45);
        v.put("acido_folico_ug", 30);
        v.put("sodio_mg", 45);
        v.put("potasio_mg", 320);
        v.put("costo_kg_soles", 22);
        return v;
    }

    private Map<String, Object> valoresGalleta() {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("energia_kcal", 420);
        v.put("agua_g", 8);
        v.put("proteinas_g", 8);
        v.put("grasa_total_g", 14);
        v.put("carbohidratos_disponibles_g", 62);
        v.put("fibra_g", 4);
        v.put("cenizas_g", 1.5);
        v.put("calcio_mg", 150);
        v.put("fosforo_mg", 180);
        v.put("zinc_mg", 2.5);
        v.put("hierro_mg", 8);
        v.put("beta_caroteno_ug", 200);
        v.put("vitamina_a_ug", 40);
        v.put("tiamina_mg", 0.25);
        v.put("riboflavina_mg", 0.2);
        v.put("niacina_mg", 3);
        v.put("vitamina_c_mg", 5);
        v.put("acido_folico_ug", 120);
        v.put("sodio_mg", 280);
        v.put("potasio_mg", 200);
        v.put("costo_kg_soles", 12);
        return v;
    }

    private static Map<String, String> campo(String key, String label, String unidad) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("unidad", unidad);
        return m;
    }
}
