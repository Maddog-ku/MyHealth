package com.myhealth.auth;

import com.myhealth.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final String DEFAULT_SECRET = "change-me-to-a-base64-or-long-random-secret-at-least-32-bytes";

    private final AppProperties properties;
    private final SecretKey key;

    @Autowired
    public JwtService(AppProperties properties, Environment environment) {
        this.properties = properties;
        validateSecret(properties.jwt().secret(), environment);
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    public JwtService(AppProperties properties) {
        this(properties, null);
    }

    public String issueAccessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.jwt().accessTtl());
        return Jwts.builder()
                .subject(principal.getUsername())
                .claim("uid", principal.id())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long expiresInSeconds() {
        return properties.jwt().accessTtl().toSeconds();
    }

    private void validateSecret(String secret, Environment environment) {
        boolean prod = environment != null && Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (!prod) {
            return;
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be set in prod profile");
        }
        if (DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException("JWT_SECRET must not use the default value in prod profile");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes in prod profile");
        }
    }
}
