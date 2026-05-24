package com.mikunaigen.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.mikunaigen.backend.repository.sql")
public class JpaConfig {
}
