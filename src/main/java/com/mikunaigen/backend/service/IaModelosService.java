package com.mikunaigen.backend.service;

import com.mikunaigen.backend.model.sql.ConfiguracionIa;
import com.mikunaigen.backend.repository.sql.ConfiguracionIaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class IaModelosService {

    private final ConfiguracionIaRepository repo;
    private final Path modelsDir;

    public IaModelosService(
            ConfiguracionIaRepository repo,
            @Value("${app.ia.models.dir:ia-models}") String modelsDir
    ) throws IOException {
        this.repo = repo;
        this.modelsDir = Path.of(modelsDir).toAbsolutePath().normalize();
        Files.createDirectories(this.modelsDir);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> configuracionCompleta() {
        return respuestaDesde(getOrCreate());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> estadoPublico() {
        ConfiguracionIa c = getOrCreate();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("iaActiva", c.isIaActiva());
        out.put("slot1Enabled", c.isSlot1Enabled());
        out.put("slot2Enabled", c.isSlot2Enabled());
        out.put("slot3Enabled", c.isSlot3Enabled());
        out.put("slot1Activo", slotEfectivo(c, 1));
        out.put("slot2Activo", slotEfectivo(c, 2));
        out.put("slot3Activo", slotEfectivo(c, 3));
        return out;
    }

    public boolean slotEfectivo(ConfiguracionIa c, int slot) {
        if (c == null || !c.isIaActiva()) {
            return false;
        }
        return switch (slot) {
            case 1 -> c.isSlot1Enabled();
            case 2 -> c.isSlot2Enabled();
            case 3 -> c.isSlot3Enabled();
            default -> false;
        };
    }

    @Transactional(readOnly = true)
    public boolean slotEfectivo(int slot) {
        return slotEfectivo(getOrCreate(), slot);
    }

    @Transactional
    public Map<String, Object> toggleIa(boolean iaActiva) {
        ConfiguracionIa c = getOrCreate();
        c.setIaActiva(iaActiva);
        c.setSlot1Enabled(iaActiva);
        c.setSlot2Enabled(iaActiva);
        c.setSlot3Enabled(iaActiva);
        c.setActualizadoEn(LocalDateTime.now());
        repo.save(c);
        return respuestaDesde(c);
    }

    @Transactional
    public Map<String, Object> toggleSlot(int slotNumber, boolean enabled) {
        if (slotNumber < 1 || slotNumber > 3) {
            throw new IllegalArgumentException("Slot inválido.");
        }
        ConfiguracionIa c = getOrCreate();
        switch (slotNumber) {
            case 1 -> c.setSlot1Enabled(enabled);
            case 2 -> c.setSlot2Enabled(enabled);
            case 3 -> c.setSlot3Enabled(enabled);
            default -> {
            }
        }
        c.setActualizadoEn(LocalDateTime.now());
        repo.save(c);
        return respuestaDesde(c);
    }

    @Transactional
    public Map<String, Object> uploadSlot1(Map<String, Object> body) throws IOException {
        ConfiguracionIa c = getOrCreate();
        saveBase64File(body, "modelFileBase64", "modelFileName", "slot1");
        saveBase64File(body, "encodersFileBase64", "encodersFileName", "slot1");
        c.setSlot1ModelFileName(str(body.get("modelFileName")));
        c.setSlot1EncodersFileName(str(body.get("encodersFileName")));
        c.setSlot1UploadedAt(LocalDateTime.now());
        c.setActualizadoEn(LocalDateTime.now());
        repo.save(c);
        return respuestaDesde(c);
    }

    @Transactional
    public Map<String, Object> uploadSlot2(Map<String, Object> body) throws IOException {
        ConfiguracionIa c = getOrCreate();
        saveBase64File(body, "rulesFileBase64", "rulesFileName", "slot2");
        saveBase64File(body, "frequencyFileBase64", "frequencyFileName", "slot2");
        saveBase64File(body, "configFileBase64", "configFileName", "slot2");
        c.setSlot2RulesFileName(str(body.get("rulesFileName")));
        c.setSlot2FrequencyFileName(str(body.get("frequencyFileName")));
        c.setSlot2ConfigFileName(str(body.get("configFileName")));
        c.setSlot2UploadedAt(LocalDateTime.now());
        c.setActualizadoEn(LocalDateTime.now());
        repo.save(c);
        return respuestaDesde(c);
    }

    @Transactional
    public Map<String, Object> uploadSlot3(Map<String, Object> body) throws IOException {
        ConfiguracionIa c = getOrCreate();
        saveBase64File(body, "modelFileBase64", "modelFileName", "slot3");
        saveBase64File(body, "featScalerBase64", "featScalerFileName", "slot3");
        saveBase64File(body, "yScalerBase64", "yScalerFileName", "slot3");
        saveBase64File(body, "metaModeloBase64", "metaModeloFileName", "slot3");
        c.setSlot3ModelFileName(str(body.get("modelFileName")));
        c.setSlot3FeatScalerFileName(str(body.get("featScalerFileName")));
        c.setSlot3YScalerFileName(str(body.get("yScalerFileName")));
        c.setSlot3MetaModeloFileName(str(body.get("metaModeloFileName")));
        c.setSlot3UploadedAt(LocalDateTime.now());
        c.setActualizadoEn(LocalDateTime.now());
        repo.save(c);
        return respuestaDesde(c);
    }

    public Map<String, Object> solicitarDataset(int slot) {
        return Map.of(
                "message", "Generación de dataset no disponible en este entorno.",
                "fileName", "dataset_modelo_" + String.format("%02d", slot) + ".zip",
                "jobId", UUID.randomUUID().toString()
        );
    }

    public Map<String, Object> estadoDatasetJob(String jobId) {
        return Map.of(
                "status", "FAILED",
                "message", "Generación de dataset no disponible.",
                "jobId", jobId
        );
    }

    private ConfiguracionIa getOrCreate() {
        return repo.findById(1).orElseGet(() -> {
            ConfiguracionIa c = new ConfiguracionIa();
            c.setId(1);
            return repo.save(c);
        });
    }

    private Map<String, Object> respuestaDesde(ConfiguracionIa c) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("iaActiva", c.isIaActiva());
        out.put("slots", List.of(
                slotDto(1, "Recomendaciones en menú", c),
                slotDto(2, "Venta cruzada en carrito", c),
                slotDto(3, "Predicción de inventario", c)
        ));
        return out;
    }

    private Map<String, Object> slotDto(int number, String titulo, ConfiguracionIa c) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("slotNumber", number);
        s.put("titulo", titulo);
        s.put("slotEnabled", slotEnabledFlag(c, number));
        s.put("status", slotStatus(c, number));
        if (number == 1) {
            s.put("modelFileName", c.getSlot1ModelFileName());
            s.put("encodersFileName", c.getSlot1EncodersFileName());
            s.put("uploadedAt", c.getSlot1UploadedAt());
        } else if (number == 2) {
            s.put("rulesFileName", c.getSlot2RulesFileName());
            s.put("frequencyFileName", c.getSlot2FrequencyFileName());
            s.put("configFileName", c.getSlot2ConfigFileName());
            s.put("uploadedAt", c.getSlot2UploadedAt());
        } else {
            s.put("modelFileName", c.getSlot3ModelFileName());
            s.put("featScalerFileName", c.getSlot3FeatScalerFileName());
            s.put("yScalerFileName", c.getSlot3YScalerFileName());
            s.put("metaModeloFileName", c.getSlot3MetaModeloFileName());
            s.put("uploadedAt", c.getSlot3UploadedAt());
        }
        return s;
    }

    private boolean slotEnabledFlag(ConfiguracionIa c, int number) {
        return switch (number) {
            case 1 -> c.isSlot1Enabled();
            case 2 -> c.isSlot2Enabled();
            case 3 -> c.isSlot3Enabled();
            default -> false;
        };
    }

    private String slotStatus(ConfiguracionIa c, int number) {
        boolean hasFiles = switch (number) {
            case 1 -> notBlank(c.getSlot1ModelFileName()) && notBlank(c.getSlot1EncodersFileName());
            case 2 -> notBlank(c.getSlot2RulesFileName())
                    && notBlank(c.getSlot2FrequencyFileName())
                    && notBlank(c.getSlot2ConfigFileName());
            case 3 -> notBlank(c.getSlot3ModelFileName())
                    && notBlank(c.getSlot3FeatScalerFileName())
                    && notBlank(c.getSlot3YScalerFileName())
                    && notBlank(c.getSlot3MetaModeloFileName());
            default -> false;
        };
        return hasFiles ? "ACTIVO" : "VACIO";
    }

    private void saveBase64File(Map<String, Object> body, String base64Key, String nameKey, String slotFolder)
            throws IOException {
        String base64 = str(body.get(base64Key));
        String fileName = str(body.get(nameKey));
        if (base64.isBlank() || fileName.isBlank()) {
            return;
        }
        String payload = base64;
        int comma = payload.indexOf(',');
        if (comma >= 0) {
            payload = payload.substring(comma + 1);
        }
        byte[] bytes = Base64.getDecoder().decode(payload);
        Path dir = modelsDir.resolve(slotFolder);
        Files.createDirectories(dir);
        Files.write(dir.resolve(sanitize(fileName)), bytes);
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String str(Object o) {
        return o != null ? String.valueOf(o).trim() : "";
    }
}
