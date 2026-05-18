package com.mikunaigen.backend.controller;

import com.mikunaigen.backend.service.AiDatasetDispatchService;
import com.mikunaigen.backend.service.AiDatasetJobService;
import com.mikunaigen.backend.service.AiModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;


@RestController
@RequestMapping("/api/ia-modelos")
public class AiModelController {

    private static final Logger log = LoggerFactory.getLogger(AiModelController.class);

    @Autowired
    private AiModelService aiModelService;

    @Autowired
    private AiDatasetDispatchService aiDatasetDispatchService;

    @Autowired
    private AiDatasetJobService aiDatasetJobService;

    private static int len(String s) {
        return s == null ? 0 : s.length();
    }

    @GetMapping
    public ResponseEntity<?> obtenerConfigAdmin() {
        return ResponseEntity.ok(aiModelService.obtenerConfigAdmin());
    }

    @GetMapping("/publico")
    public ResponseEntity<?> obtenerConfigPublica() {
        return ResponseEntity.ok(aiModelService.obtenerConfigPublica());
    }

    @PostMapping("/dataset/{slot}/solicitar")
    public ResponseEntity<?> solicitarDataset(@PathVariable int slot) {
        try {
            return ResponseEntity.accepted().body(aiDatasetDispatchService.solicitarGeneracion(slot));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("[DATASET] Error solicitando slot={}", slot, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "No se pudo iniciar la generación del dataset."));
        }
    }

    @GetMapping("/dataset/jobs/{jobId}")
    public ResponseEntity<?> estadoDataset(@PathVariable String jobId) {
        try {
            return ResponseEntity.ok(aiDatasetJobService.estadoPublico(jobId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/toggle")
    public ResponseEntity<?> toggleIa(@RequestBody Map<String, Object> body) {
        Object val = body == null ? null : body.get("iaActiva");
        if (val == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "iaActiva es obligatorio."));
        }
        boolean iaActiva = val instanceof Boolean ? (Boolean) val : Boolean.parseBoolean(String.valueOf(val));
        return ResponseEntity.ok(aiModelService.actualizarIaActiva(iaActiva));
    }

    @PatchMapping("/slot/{slotNumber}/toggle")
    public ResponseEntity<?> toggleSlot(@PathVariable int slotNumber, @RequestBody Map<String, Object> body) {
        Object val = body == null ? null : body.get("enabled");
        if (val == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "enabled es obligatorio."));
        }
        boolean enabled = val instanceof Boolean ? (Boolean) val : Boolean.parseBoolean(String.valueOf(val));
        return ResponseEntity.ok(aiModelService.actualizarSlotEnabled(slotNumber, enabled));
    }

    @PostMapping("/slot-1/upload")
    public ResponseEntity<?> subirSlot1(@RequestBody Map<String, String> body) {
        try {
            String modelFileName = body == null ? null : body.get("modelFileName");
            String modelFileBase64 = body == null ? null : body.get("modelFileBase64");
            String encodersFileName = body == null ? null : body.get("encodersFileName");
            String encodersFileBase64 = body == null ? null : body.get("encodersFileBase64");
            return ResponseEntity.ok(aiModelService.subirArchivosSlot1(
                    modelFileName,
                    modelFileBase64,
                    encodersFileName,
                    encodersFileBase64
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "No se pudo cargar el modelo."));
        }
    }

    @PostMapping("/slot-2/upload")
    public ResponseEntity<?> subirSlot2(@RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(aiModelService.subirArchivosSlot2(
                    body == null ? null : body.get("rulesFileName"),
                    body == null ? null : body.get("rulesFileBase64"),
                    body == null ? null : body.get("frequencyFileName"),
                    body == null ? null : body.get("frequencyFileBase64"),
                    body == null ? null : body.get("configFileName"),
                    body == null ? null : body.get("configFileBase64")
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "No se pudo cargar el paquete de reglas del Slot 2."));
        }
    }

    @PostMapping("/slot-3/upload")
    public ResponseEntity<?> subirSlot3(@RequestBody Map<String, String> body) {
        String modelFn = body == null ? null : body.get("modelFileName");
        String featFn = body == null ? null : body.get("featScalerFileName");
        String yFn = body == null ? null : body.get("yScalerFileName");
        String metaFn = body == null ? null : body.get("metaModeloFileName");
        log.info("[SLOT3-UPLOAD] Controller: petición recibida model={} feat={} y={} meta={}",
                modelFn, featFn, yFn, metaFn);
        log.debug("[SLOT3-UPLOAD] Controller: longitudes base64 model={} feat={} y={} meta={}",
                len(body == null ? null : body.get("modelFileBase64")),
                len(body == null ? null : body.get("featScalerBase64")),
                len(body == null ? null : body.get("yScalerBase64")),
                len(body == null ? null : body.get("metaModeloBase64")));
        try {
            var out = aiModelService.subirArchivosSlot3(
                    body == null ? null : body.get("modelFileName"),
                    body == null ? null : body.get("modelFileBase64"),
                    body == null ? null : body.get("featScalerFileName"),
                    body == null ? null : body.get("featScalerBase64"),
                    body == null ? null : body.get("yScalerFileName"),
                    body == null ? null : body.get("yScalerBase64"),
                    body == null ? null : body.get("metaModeloFileName"),
                    body == null ? null : body.get("metaModeloBase64")
            );
            log.info("[SLOT3-UPLOAD] Controller: subida OK slots en respuesta={}",
                    out.get("slots") instanceof java.util.List<?> l ? l.size() : -1);
            return ResponseEntity.ok(out);
        } catch (IllegalArgumentException e) {
            log.warn("[SLOT3-UPLOAD] Controller: validación o meta falló: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("[SLOT3-UPLOAD] Controller: error no controlado", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "No se pudo cargar el paquete del Slot 3."));
        }
    }
}
