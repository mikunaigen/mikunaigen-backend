package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.model.sql.User;
import com.mikunaigen.backend.repository.sql.UserRepository;
import com.mikunaigen.backend.service.AdminAlimentoDatasetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/alimentos-dataset")
public class AdminAlimentoDatasetController {

    private final AdminAlimentoDatasetService datasetService;
    private final UserRepository userRepository;

    public AdminAlimentoDatasetController(
            AdminAlimentoDatasetService datasetService,
            UserRepository userRepository
    ) {
        this.datasetService = datasetService;
        this.userRepository = userRepository;
    }

    @GetMapping("/estado")
    public ResponseEntity<Map<String, Object>> estado() {
        return ResponseEntity.ok(datasetService.estado());
    }

    @GetMapping("/filtros")
    public ResponseEntity<Map<String, Object>> filtros() {
        return ResponseEntity.ok(datasetService.metadatosFiltros());
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listar(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String grupo,
            @RequestParam(required = false) String campoNutricional,
            @RequestParam(required = false) String rangoFiltro,
            @RequestParam(required = false) BigDecimal min,
            @RequestParam(required = false) BigDecimal max
    ) {
        List<Map<String, Object>> alimentos = datasetService.listar(nombre, grupo, campoNutricional, rangoFiltro, min, max);
        return ResponseEntity.ok(Map.of("alimentos", alimentos, "total", alimentos.size()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(datasetService.crear(body, adminId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizar(
            @PathVariable Integer id,
            @RequestBody Map<String, Object> body
    ) {
        return ResponseEntity.ok(datasetService.actualizar(id, body, adminId()));
    }

    @PutMapping("/lote")
    public ResponseEntity<Map<String, Object>> guardarLote(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        return ResponseEntity.ok(datasetService.guardarLote(items, adminId()));
    }

    @PostMapping(value = "/importar-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importarCsv(@RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(datasetService.importarCsv(archivo, adminId()));
    }

    private UUID adminId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado."));
        return user.getId();
    }
}
