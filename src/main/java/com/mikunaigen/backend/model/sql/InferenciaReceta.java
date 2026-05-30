package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Entity
@Table(name = "inferencias_recetas")
public class InferenciaReceta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "nombre_personalizado", length = 100)
    private String nombrePersonalizado;

    @Column(nullable = false, length = 30)
    private String estado;

    @Column(name = "modo_optimizacion", nullable = false, length = 50)
    private String modoOptimizacion;

    @Column(name = "costo_estimado_kg", precision = 10, scale = 2)
    private BigDecimal costoEstimadoKg;

    @Column(name = "margen_error_mae", precision = 10, scale = 4)
    private BigDecimal margenErrorMae;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_parametros", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> inputParametros;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_nutricional_logrado", columnDefinition = "jsonb")
    private Map<String, Object> outputNutricionalLogrado;

    @Column(name = "supero_limite_seguridad")
    private Boolean superoLimiteSeguridad = false;

    @Column(name = "componente_infractor", length = 50)
    private String componenteInfractor;

    @Column(name = "valor_infractor", precision = 10, scale = 4)
    private BigDecimal valorInfractor;

    @Column(name = "es_modificada_manualmente")
    private boolean esModificadaManualmente = false;

    @Column(name = "fecha_generacion")
    private LocalDateTime fechaGeneracion = LocalDateTime.now();
}
