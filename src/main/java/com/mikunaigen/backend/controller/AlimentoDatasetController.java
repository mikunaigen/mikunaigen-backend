package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.model.sql.AlimentoDataset;
import com.mikunaigen.backend.repository.sql.AlimentoDatasetRepository;
import com.mikunaigen.backend.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/catalogo/alimentos-dataset")
public class AlimentoDatasetController {

    private static final Set<String> CATEGORIAS = Set.of(
            "Cereales", "Verduras", "Frutas", "Grasas", "Pescados", "Carnes",
            "Leche", "Bebidas", "Huevos", "Azucarados", "Preparados", "Leguminosas", "Tubérculos"
    );

    @Autowired
    private AlimentoDatasetRepository alimentoRepo;

    @Autowired
    private JwtService jwtService;

    @GetMapping
    public List<Map<String, Object>> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categoria
    ) {
        List<AlimentoDataset> lista;
        if (categoria != null && !categoria.isBlank()) {
            lista = alimentoRepo.findByCategoriaIgnoreCase(categoria.trim());
        } else if (q != null && !q.isBlank()) {
            lista = alimentoRepo.buscar(q.trim());
        } else {
            lista = alimentoRepo.findAll();
        }
        return lista.stream().map(this::aMapa).toList();
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Map<String, Object> body, @RequestHeader(value = "Authorization", required = false) String auth) {
        UUID adminId = obtenerUsuarioId(auth);
        if (adminId == null) {
            return ResponseEntity.status(401).body(Map.of("message", "No autorizado."));
        }
        AlimentoDataset a = mapaAEntidad(body, new AlimentoDataset());
        ResponseEntity<?> err = validar(a);
        if (err != null) {
            return err;
        }
        a.setModificadoPor(adminId);
        a.setFechaModificacion(LocalDateTime.now());
        alimentoRepo.save(a);
        return ResponseEntity.ok(Map.of("message", "Alimento registrado.", "id", a.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(
            @PathVariable Integer id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        UUID adminId = obtenerUsuarioId(auth);
        if (adminId == null) {
            return ResponseEntity.status(401).body(Map.of("message", "No autorizado."));
        }
        AlimentoDataset a = alimentoRepo.findById(id).orElse(null);
        if (a == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Alimento no encontrado."));
        }
        mapaAEntidad(body, a);
        ResponseEntity<?> err = validar(a);
        if (err != null) {
            return err;
        }
        a.setModificadoPor(adminId);
        a.setFechaModificacion(LocalDateTime.now());
        alimentoRepo.save(a);
        return ResponseEntity.ok(Map.of("message", "Alimento actualizado."));
    }

    private ResponseEntity<?> validar(AlimentoDataset a) {
        if (a.getNombre() == null || a.getNombre().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "El nombre es obligatorio."));
        }
        if (a.getCategoria() == null || !CATEGORIAS.contains(a.getCategoria())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Categoría inválida."));
        }
        if (a.getEnergiaKcal() == null || a.getEnergiaKcal().compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Valores numéricos no pueden ser negativos."));
        }
        if (a.getCostoKgSoles() == null || a.getCostoKgSoles().compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "El costo por kg no puede ser negativo."));
        }
        return null;
    }

    private AlimentoDataset mapaAEntidad(Map<String, Object> body, AlimentoDataset a) {
        if (body.get("nombre") != null) {
            a.setNombre(String.valueOf(body.get("nombre")).trim());
        }
        if (body.get("categoria") != null) {
            a.setCategoria(String.valueOf(body.get("categoria")).trim());
        }
        if (body.get("codigo_minsa") != null) {
            a.setCodigoMinsa(String.valueOf(body.get("codigo_minsa")).trim());
        }
        setNum(body, "energia_kcal", a::setEnergiaKcal);
        setNum(body, "costo_kg_soles", a::setCostoKgSoles);
        setNum(body, "proteinas_g", a::setProteinasG);
        setNum(body, "grasa_total_g", a::setGrasaTotalG);
        setNum(body, "fibra_g", a::setFibraG);
        setNum(body, "sodio_mg", a::setSodioMg);
        if (body.get("meses_disponibilidad") instanceof List<?> meses) {
            Integer[] arr = meses.stream()
                    .map(o -> Integer.parseInt(String.valueOf(o)))
                    .toArray(Integer[]::new);
            a.setMesesDisponibilidad(arr);
        }
        return a;
    }

    private void setNum(Map<String, Object> body, String key, java.util.function.Consumer<BigDecimal> setter) {
        if (body.get(key) == null) {
            return;
        }
        setter.accept(new BigDecimal(String.valueOf(body.get(key))));
    }

    private Map<String, Object> aMapa(AlimentoDataset a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("codigo_minsa", a.getCodigoMinsa());
        m.put("nombre", a.getNombre());
        m.put("categoria", a.getCategoria());
        m.put("energia_kcal", a.getEnergiaKcal());
        m.put("costo_kg_soles", a.getCostoKgSoles());
        m.put("meses_disponibilidad", a.getMesesDisponibilidad());
        m.put("datos_incompletos", tieneDatosFaltantes(a));
        m.put("fecha_modificacion", a.getFechaModificacion());
        return m;
    }

    private boolean tieneDatosFaltantes(AlimentoDataset a) {
        return a.getProteinasG() == null || a.getFibraG() == null || a.getSodioMg() == null;
    }

    private UUID obtenerUsuarioId(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        try {
            String userId = jwtService.parseClaims(auth.substring(7)).get("userId", String.class);
            return userId != null ? UUID.fromString(userId) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
