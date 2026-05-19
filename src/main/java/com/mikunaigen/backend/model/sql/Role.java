package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "roles")
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 50)
    private String nombre;

    public String getName() {
        return nombre;
    }

    public void setName(String name) {
        this.nombre = name;
    }
}
