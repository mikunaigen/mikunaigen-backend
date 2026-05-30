package com.mikunaigen.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;
    private final long mfaPendingExpirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs,
            @Value("${jwt.mfa-pending-expiration-ms:300000}") long mfaPendingExpirationMs
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.mfaPendingExpirationMs = mfaPendingExpirationMs;
    }

    public String generateToken(String email, String userId, String role) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(exp)
                .claims(Map.of(
                        "userId", userId,
                        "role", role
                ))
                .signWith(key)
                .compact();
    }

    public String generateMfaPendingToken(String email, String userId) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + mfaPendingExpirationMs);
        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(exp)
                .claims(Map.of(
                        "userId", userId,
                        "mfaPending", true
                ))
                .signWith(key)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean esTokenMfaPendiente(Claims claims) {
        Object valor = claims.get("mfaPending");
        return Boolean.TRUE.equals(valor) || "true".equalsIgnoreCase(String.valueOf(valor));
    }
}
