package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Integer> {
    Optional<Role> findByName(String name);
}