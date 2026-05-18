package com.mikunaigen.backend.dto;

import com.mikunaigen.backend.model.nosql.Producto;
import lombok.Data;
import java.util.List;

@Data
public class ProductoRequest {
    private Producto producto;
    private List<RecetaItemDTO> receta;

    @Data
    public static class RecetaItemDTO {
        private Integer ingredientId;
        private Double quantity;
    }
}