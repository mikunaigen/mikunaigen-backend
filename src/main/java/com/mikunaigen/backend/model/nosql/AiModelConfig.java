package com.mikunaigen.backend.model.nosql;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "ai_model_config")
public class AiModelConfig {
    @Id
    private String id;
    private boolean iaActiva = false;
    private List<ModelSlot> slots = new ArrayList<>();

    @Data
    public static class ModelSlot {
        private int slotNumber;
        private String titulo;
        private String status;
        private boolean slotEnabled = false;
        private String modelFileName;
        private String modelFileBase64;
        private String encodersFileName;
        private String encodersFileBase64;
        private String rulesFileName;
        private String rulesFileBase64;
        private String frequencyFileName;
        private String frequencyFileBase64;
        private String configFileName;
        private String configFileBase64;
        private String featScalerFileName;
        private String featScalerBase64;
        private String yScalerFileName;
        private String yScalerBase64;
        private String metaModeloFileName;
        private String metaModeloBase64;
        private String modelFileGridFsId;
        private String featScalerGridFsId;
        private String yScalerGridFsId;
        private String metaModeloGridFsId;
        private LocalDateTime uploadedAt;
    }
}
