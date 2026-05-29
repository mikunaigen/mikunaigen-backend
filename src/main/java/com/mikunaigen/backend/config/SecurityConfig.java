package com.mikunaigen.backend.config;

import com.mikunaigen.backend.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;
import org.springframework.web.cors.CorsConfiguration; 
import org.springframework.web.cors.CorsConfigurationSource; 
import org.springframework.web.cors.UrlBasedCorsConfigurationSource; 
import org.springframework.beans.factory.annotation.Value; 
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(Arrays.asList("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final MaintenanceFilter maintenanceFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, MaintenanceFilter maintenanceFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.maintenanceFilter = maintenanceFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) -> res.sendError(HttpStatus.UNAUTHORIZED.value())))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/ws-mikunaigen/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(
                        "/api/ping",
                        "/api/auth/login",
                        "/api/auth/check-admin",
                        "/api/auth/estado-usuarios",
                        "/api/auth/registrar-pendiente",
                        "/api/auth/registrar-admin",
                        "/api/auth/estado-activacion/**",
                        "/api/auth/renovar-codigo-activacion/**",
                        "/api/auth/cancelar-registro-pendiente/**",
                        "/api/auth/info-telegram",
                        "/api/auth/enviar-codigo-empleado",
                        "/api/auth/confirmar-empleado",
                        "/api/auth/enviar-codigo-recuperacion",
                        "/api/auth/reset-password",
                        "/api/auth/ip-status",
                        "/api/configuracion/**",
                        "/api/estado_bases_datos",
                        "/api/client-errors/report",
                        "/api/webhooks/backup-workflow",
                        "/api/webhooks/dataset-workflow",
                        "/api/webhooks/dashboard-report-workflow",
                        "/api/webhooks/dashboard-export/**",
                        "/api/webhooks/backup-cron",
                        "/api/webhooks/email-dispatch",
                        "/api/webhooks/maintenance-end",
                        "/api/webhooks/maintenance-status",
                        "/api/webhooks/entrenamiento-dataset",
                        "/api/webhooks/kaggle-entrenamiento"
                ).permitAll()
                .requestMatchers("/api/admin/**").hasRole("administrador")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .addFilterBefore(maintenanceFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
