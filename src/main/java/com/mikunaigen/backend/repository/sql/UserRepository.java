package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByDni(String dni);
    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByPhone(String phone);
    boolean existsByPhoneAndIdNot(String phone, UUID id);
    List<User> findByRole_NameAndIsDeletedFalse(String roleName);

    long countByRole_NameAndIsDeletedFalseAndCreatedAtBetween(String roleName, LocalDateTime from, LocalDateTime toExclusive);

    @Query("SELECT COUNT(u) FROM User u WHERE u.isDeleted = false AND u.role.name = 'CLIENTE'")
    long countActiveClientes();

    @Query(value = """
            SELECT u.id, u.full_name
            FROM users u
            WHERE u.id IN :ids
            """, nativeQuery = true)
    List<Object[]> findNamesByIds(@Param("ids") List<UUID> ids);

    @Query("SELECT MIN(u.createdAt) FROM User u WHERE COALESCE(u.isDeleted, false) = false AND u.role.name = 'CLIENTE'")
    Optional<LocalDateTime> findMinClienteCreatedAt();
}
