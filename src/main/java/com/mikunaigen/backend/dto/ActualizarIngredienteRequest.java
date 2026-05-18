package com.mikunaigen.backend.dto;

import lombok.Data;

@Data
public class ActualizarIngredienteRequest {
    private String name;
    private String category;
    private String unit;
    private Double stockQuantity;
    private Double price;
    private String imageBase64;
    private Boolean confirmarCambioUnidad;
}
