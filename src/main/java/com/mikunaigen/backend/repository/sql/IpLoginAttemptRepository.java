package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.IpLoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IpLoginAttemptRepository extends JpaRepository<IpLoginAttempt, Integer> {
    Optional<IpLoginAttempt> findByIpAddress(String ipAddress);

    long countByBlockedUntilAfter(LocalDateTime now);

    @Query(value = """
            SELECT ip_address, COALESCE(failed_attempts, 0)
            FROM ip_login_attempts
            ORDER BY failed_attempts DESC NULLS LAST
            LIMIT 10
            """, nativeQuery = true)
    List<Object[]> findTop10ByFailedAttempts();
}

