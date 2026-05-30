package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.sql.LimitesNormativos;
import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.LimitesNormativosRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class LimitesNormativosService {

    public static final String NORMATIVA_CODEX = "codex_alimentarius";
    public static final String NORMATIVA_LEY = "ley_30021";

    private static final Map<String, String> ETIQUETAS_NUTRIENTE = Map.ofEntries(
            Map.entry("sodio_mg", "Sodio"),
            Map.entry("grasa_total_g", "Grasa total"),
            Map.entry("energia_kcal", "Energía"),
            Map.entry("hierro_mg", "Hierro"),
            Map.entry("zinc_mg", "Zinc"),
            Map.entry("vitamina_c_mg", "Vitamina C"),
            Map.entry("carbohidratos_disponibles_g", "Carbohidratos disponibles")
    );

    private final LimitesNormativosRepository repository;
    private final UserRepository userRepository;

    public LimitesNormativosService(LimitesNormativosRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarParaAdmin() {
        List<Map<String, Object>> filas = new ArrayList<>();
        for (LimitesNormativos limite : repository.findAllByOrderByNormativaAscNutrienteAsc()) {
            filas.add(mapaLimite(limite));
        }
        return filas;
    }

    @Transactional
    public List<Map<String, Object>> actualizarDesdeAdmin(UUID adminId, List<Map<String, Object>> cambios) {
        if (cambios == null || cambios.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay cambios para guardar.");
        }
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Administrador no válido."));
        LocalDateTime ahora = LocalDateTime.now();

        for (Map<String, Object> cambio : cambios) {
            Integer id = parseId(cambio.get("id"));
            if (id == null) {
                continue;
            }
            LimitesNormativos entidad = repository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Límite normativo no encontrado: " + id));

            Object valorRaw = cambio.get("valorMaximo");
            if (valorRaw != null) {
                BigDecimal valor = new BigDecimal(String.valueOf(valorRaw));
                if (valor.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "El valor máximo debe ser mayor que cero para " + entidad.getNutriente() + ".");
                }
                entidad.setValorMaximo(valor);
            }

            if (cambio.containsKey("descripcion")) {
                String desc = cambio.get("descripcion") != null
                        ? String.valueOf(cambio.get("descripcion")).trim()
                        : null;
                entidad.setDescripcion(desc == null || desc.isBlank() ? null : desc);
            }

            entidad.setActualizadoEn(ahora);
            entidad.setActualizadoPor(admin.getId());
            repository.save(entidad);
        }

        return listarParaAdmin();
    }

    @Transactional(readOnly = true)
    public Map<String, Double> mapaLimitesCodex() {
        return mapaPorNormativa(NORMATIVA_CODEX);
    }

    @Transactional(readOnly = true)
    public Map<String, Double> mapaUmbralesLey30021() {
        return mapaPorNormativa(NORMATIVA_LEY);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> mapaNormativasParaInferencia() {
        Map<String, Object> normativas = new LinkedHashMap<>();
        normativas.put("limites_codex", mapaLimitesCodex());
        normativas.put("umbrales_ley_30021", mapaUmbralesLey30021());
        return normativas;
    }

    @Transactional(readOnly = true)
    public Optional<LimitesNormativos> buscarPorNormativaYNutriente(String normativa, String nutriente) {
        return repository.findByNormativaOrderByNutrienteAsc(normativa).stream()
                .filter(l -> nutriente.equals(l.getNutriente()))
                .findFirst();
    }

    private Map<String, Double> mapaPorNormativa(String normativa) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (LimitesNormativos limite : repository.findByNormativaOrderByNutrienteAsc(normativa)) {
            out.put(limite.getNutriente(), limite.getValorMaximo().doubleValue());
        }
        return out;
    }

    private Map<String, Object> mapaLimite(LimitesNormativos limite) {
        Map<String, Object> fila = new LinkedHashMap<>();
        fila.put("id", limite.getId());
        fila.put("normativa", limite.getNormativa());
        fila.put("nutriente", limite.getNutriente());
        fila.put("etiquetaNutriente", ETIQUETAS_NUTRIENTE.getOrDefault(limite.getNutriente(), limite.getNutriente()));
        fila.put("valorMaximo", limite.getValorMaximo());
        fila.put("unidad", limite.getUnidad());
        fila.put("descripcion", limite.getDescripcion());
        fila.put("actualizadoEn", limite.getActualizadoEn() != null ? limite.getActualizadoEn().toString() : null);
        fila.put("actualizadoPor", limite.getActualizadoPor() != null ? limite.getActualizadoPor().toString() : null);
        if (limite.getActualizadoPor() != null) {
            userRepository.findById(limite.getActualizadoPor()).ifPresent(u ->
                    fila.put("actualizadoPorEmail", u.getEmail()));
        }
        return fila;
    }

    private Integer parseId(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
