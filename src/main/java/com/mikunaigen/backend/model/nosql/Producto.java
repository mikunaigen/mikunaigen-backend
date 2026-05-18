package com.mikunaigen.backend.model.nosql;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Data
@Document(collection = "products")
public class Producto {
    @Id
    private String id;
    private String name;
    private Double price;
    private String category;
    private String description;
    private List<String> imagesBase64;
    private boolean active = true;
    private boolean isDeleted = false;
}