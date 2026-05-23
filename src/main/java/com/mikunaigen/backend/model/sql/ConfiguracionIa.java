package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "configuracion_ia")
public class ConfiguracionIa {

    @Id
    private Integer id = 1;

    @Column(name = "ia_activa", nullable = false)
    private boolean iaActiva = false;

    @Column(name = "slot1_enabled", nullable = false)
    private boolean slot1Enabled = false;

    @Column(name = "slot2_enabled", nullable = false)
    private boolean slot2Enabled = false;

    @Column(name = "slot3_enabled", nullable = false)
    private boolean slot3Enabled = false;

    @Column(name = "slot1_model_file")
    private String slot1ModelFileName;

    @Column(name = "slot1_encoders_file")
    private String slot1EncodersFileName;

    @Column(name = "slot1_uploaded_at")
    private LocalDateTime slot1UploadedAt;

    @Column(name = "slot2_rules_file")
    private String slot2RulesFileName;

    @Column(name = "slot2_frequency_file")
    private String slot2FrequencyFileName;

    @Column(name = "slot2_config_file")
    private String slot2ConfigFileName;

    @Column(name = "slot2_uploaded_at")
    private LocalDateTime slot2UploadedAt;

    @Column(name = "slot3_model_file")
    private String slot3ModelFileName;

    @Column(name = "slot3_feat_scaler_file")
    private String slot3FeatScalerFileName;

    @Column(name = "slot3_y_scaler_file")
    private String slot3YScalerFileName;

    @Column(name = "slot3_meta_file")
    private String slot3MetaModeloFileName;

    @Column(name = "slot3_uploaded_at")
    private LocalDateTime slot3UploadedAt;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn = LocalDateTime.now();
}
