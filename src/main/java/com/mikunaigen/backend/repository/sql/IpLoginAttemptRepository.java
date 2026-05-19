package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.IpLoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IpLoginAttemptRepository extends JpaRepository<IpLoginAttempt, Integer> {
    Optional<IpLoginAttempt> findByIpAddress(String ipAddress);

    long countByBlockedUntilAfter(LocalDateTime now);
}

