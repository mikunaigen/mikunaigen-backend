package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
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

    @Column(name = "entrenamiento_estado", length = 40)
    private String entrenamientoEstado = "IDLE";

    @Column(name = "entrenamiento_job_id", length = 64)
    private String entrenamientoJobId;

    @Column(name = "entrenamiento_epoca")
    private Integer entrenamientoEpoca;

    @Column(name = "entrenamiento_epocas_totales")
    private Integer entrenamientoEpocasTotales = 50;

    @Column(name = "entrenamiento_error_train", precision = 12, scale = 6)
    private BigDecimal entrenamientoErrorTrain;

    @Column(name = "entrenamiento_error_val", precision = 12, scale = 6)
    private BigDecimal entrenamientoErrorVal;

    @Column(name = "entrenamiento_loss_train", precision = 12, scale = 6)
    private BigDecimal entrenamientoLossTrain;

    @Column(name = "entrenamiento_loss_val", precision = 12, scale = 6)
    private BigDecimal entrenamientoLossVal;

    @Column(name = "entrenamiento_mensaje", length = 500)
    private String entrenamientoMensaje;

    @Column(name = "entrenamiento_dataset_b2_key", length = 200)
    private String entrenamientoDatasetB2Key;

    @Column(name = "formulacion_modelo_b2_key", length = 300)
    private String formulacionModeloB2Key;

    @Column(name = "formulacion_escalador_b2_key", length = 300)
    private String formulacionEscaladorB2Key;

    @Column(name = "entrenamiento_curva_json", columnDefinition = "TEXT")
    private String entrenamientoCurvaJson;

    @Column(name = "entrenamiento_iniciado_en")
    private LocalDateTime entrenamientoIniciadoEn;

    @Column(name = "entrenamiento_finalizado_en")
    private LocalDateTime entrenamientoFinalizadoEn;
}
