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

    default boolean existsByDni(String dni) {
        return false;
    }

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByTelegramId(String telegramId);

    boolean existsByTelefono(String telefono);

    default boolean existsByPhone(String phone) {
        return existsByTelefono(phone);
    }

    default boolean existsByPhoneAndIdNot(String phone, UUID id) {
        return existsByTelefonoAndIdNot(phone, id);
    }

    boolean existsByTelefonoAndIdNot(String telefono, UUID id);

    @Query("SELECT u FROM User u WHERE u.role.nombre = :rol AND u.estado <> 'suspendido'")
    List<User> findByRole_NameAndIsDeletedFalse(@Param("rol") String roleName);

    @Query("SELECT COUNT(u) FROM User u WHERE u.role.nombre = :rol AND u.estado <> 'suspendido' AND u.fechaRegistro >= :desde AND u.fechaRegistro < :hasta")
    long countByRole_NameAndIsDeletedFalseAndCreatedAtBetween(
            @Param("rol") String roleName,
            @Param("desde") LocalDateTime from,
            @Param("hasta") LocalDateTime toExclusive);

    @Query("SELECT COUNT(u) FROM User u WHERE u.estado <> 'suspendido' AND u.role.nombre IN ('estudiante', 'emprendedor', 'nutricionista')")
    long countActiveClientes();

    @Query(value = """
            SELECT u.id, CONCAT(u.nombres, ' ', u.apellidos)
            FROM usuarios u
            WHERE u.id IN :ids
            """, nativeQuery = true)
    List<Object[]> findNamesByIds(@Param("ids") List<UUID> ids);

    @Query("SELECT MIN(u.fechaRegistro) FROM User u WHERE u.estado <> 'suspendido' AND u.role.nombre IN ('estudiante', 'emprendedor', 'nutricionista')")
    Optional<LocalDateTime> findMinClienteCreatedAt();
}
