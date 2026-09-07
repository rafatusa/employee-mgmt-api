package com.udap.employee.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Issues and validates the API's JSON Web Tokens. */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long ttlSeconds;

    public JwtService(
            @Value("${app.jwt.secret}") final String secret,
            @Value("${app.jwt.ttl-seconds:3600}") final long tokenTtlSeconds) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = tokenTtlSeconds;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public String generateToken(final String username, final String role) {
        final Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(final String token) {
        return parse(token).getSubject();
    }

    public String extractRole(final String token) {
        final Object role = parse(token).get("role");
        return role == null ? "USER" : role.toString();
    }

    public boolean isValid(final String token) {
        try {
            return parse(token).getExpiration().after(new Date());
        } catch (final RuntimeException ex) {
            return false;
        }
    }

    private Claims parse(final String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
