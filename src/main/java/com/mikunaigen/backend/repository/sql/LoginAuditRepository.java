package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.LoginAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LoginAuditRepository extends JpaRepository<LoginAudit, Integer> {
    List<LoginAudit> findByAttemptedAtBetween(LocalDateTime from, LocalDateTime to);

    @Query(value = """
            SELECT la.id AS id, la.user_email AS user_email, la.ip_address AS ip_address, la.user_agent AS user_agent,
                   CAST(la.status AS varchar) AS status, la.failure_reason AS failure_reason, la.attempted_at AS attempted_at
            FROM login_audit la
            WHERE la.attempted_at >= :from AND la.attempted_at < :toEx
            AND (:st IS NULL OR LOWER(CAST(la.status AS varchar)) = LOWER(CAST(:st AS varchar)))
            AND (:rol IS NULL OR EXISTS (
                SELECT 1 FROM users u
                INNER JOIN roles r ON r.id = u.role_id
                WHERE u.email = la.user_email
                AND COALESCE(u.is_deleted, false) = false
                AND LOWER(CAST(r.name AS varchar)) = LOWER(CAST(:rol AS varchar))
            ))
            """, nativeQuery = true)
    List<LoginAudit> findForDashboard(
            @Param("from") LocalDateTime from,
            @Param("toEx") LocalDateTime toExclusive,
            @Param("st") String status,
            @Param("rol") String rol
    );

    @Query("SELECT MIN(la.attemptedAt) FROM LoginAudit la")
    Optional<LocalDateTime> findMinAttemptedAt();
}

