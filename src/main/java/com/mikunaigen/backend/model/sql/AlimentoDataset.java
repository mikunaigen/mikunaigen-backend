package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "alimentos_dataset")
public class AlimentoDataset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "codigo_minsa", unique = true, length = 20)
    private String codigoMinsa;

    @Column(nullable = false, unique = true, length = 150)
    private String nombre;

    @Column(nullable = false, length = 50)
    private String categoria;

    @Column(name = "energia_kcal", nullable = false, precision = 10, scale = 4)
    private BigDecimal energiaKcal = BigDecimal.ZERO;

    @Column(name = "agua_g", precision = 10, scale = 4)
    private BigDecimal aguaG;

    @Column(name = "proteinas_g", precision = 10, scale = 4)
    private BigDecimal proteinasG;

    @Column(name = "grasa_total_g", precision = 10, scale = 4)
    private BigDecimal grasaTotalG;

    @Column(name = "carbohidratos_totales_g", precision = 10, scale = 4)
    private BigDecimal carbohidratosTotalesG;

    @Column(name = "carbohidratos_disponibles_g", precision = 10, scale = 4)
    private BigDecimal carbohidratosDisponiblesG;

    @Column(name = "fibra_g", precision = 10, scale = 4)
    private BigDecimal fibraG;

    @Column(name = "cenizas_g", precision = 10, scale = 4)
    private BigDecimal cenizasG;

    @Column(name = "calcio_mg", precision = 10, scale = 4)
    private BigDecimal calcioMg;

    @Column(name = "fosforo_mg", precision = 10, scale = 4)
    private BigDecimal fosforoMg;

    @Column(name = "zinc_mg", precision = 10, scale = 4)
    private BigDecimal zincMg;

    @Column(name = "hierro_mg", precision = 10, scale = 4)
    private BigDecimal hierroMg;

    @Column(name = "beta_caroteno_ug", precision = 10, scale = 4)
    private BigDecimal betaCarotenoUg;

    @Column(name = "vitamina_a_ug", precision = 10, scale = 4)
    private BigDecimal vitaminaAUg;

    @Column(name = "tiamina_mg", precision = 10, scale = 4)
    private BigDecimal tiaminaMg;

    @Column(name = "riboflavina_mg", precision = 10, scale = 4)
    private BigDecimal riboflavinaMg;

    @Column(name = "niacina_mg", precision = 10, scale = 4)
    private BigDecimal niacinaMg;

    @Column(name = "vitamina_c_mg", precision = 10, scale = 4)
    private BigDecimal vitaminaCMg;

    @Column(name = "acido_folico_ug", precision = 10, scale = 4)
    private BigDecimal acidoFolicoUg;

    @Column(name = "sodio_mg", precision = 10, scale = 4)
    private BigDecimal sodioMg;

    @Column(name = "potasio_mg", precision = 10, scale = 4)
    private BigDecimal potasioMg;

    @Column(name = "costo_kg_soles", nullable = false, precision = 10, scale = 2)
    private BigDecimal costoKgSoles = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "meses_disponibilidad", nullable = false, columnDefinition = "integer[]")
    private Integer[] mesesDisponibilidad = new Integer[0];

    @Column(name = "modificado_por")
    private UUID modificadoPor;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion = LocalDateTime.now();
}
