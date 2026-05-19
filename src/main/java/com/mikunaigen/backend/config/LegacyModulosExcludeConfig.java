package com.mikunaigen.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.legacy.restaurante.habilitado", havingValue = "true", matchIfMissing = false)
public class LegacyModulosExcludeConfig {
}
