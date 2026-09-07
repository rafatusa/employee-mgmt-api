package com.udap.employee.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final String signingKey = TestKeys.signingKey();
    private final JwtService jwtService = new JwtService(signingKey, 3600L);

    @Test
    void generatedTokenCarriesSubjectAndRole() {
        final String token = jwtService.generateToken("alice", "ADMIN");

        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
        assertThat(jwtService.extractRole(token)).isEqualTo("ADMIN");
        assertThat(jwtService.isValid(token)).isTrue();
        assertThat(jwtService.getTtlSeconds()).isEqualTo(3600L);
    }

    @Test
    void tokenWithoutRoleClaimDefaultsToUser() {
        final String token = Jwts.builder()
                .subject("bob")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(signingKey.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(jwtService.extractRole(token)).isEqualTo("USER");
    }

    @Test
    void malformedTokenIsNotValid() {
        assertThat(jwtService.isValid("not-a-jwt")).isFalse();
    }

    @Test
    void tokenSignedWithAnotherKeyIsNotValid() {
        final String foreign = Jwts.builder()
                .subject("mallory")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(
                        TestKeys.signingKey().getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(jwtService.isValid(foreign)).isFalse();
    }

    @Test
    void expiredTokenIsNotValid() {
        final JwtService shortLived = new JwtService(signingKey, -60L);
        final String token = shortLived.generateToken("carol", "USER");

        assertThat(shortLived.isValid(token)).isFalse();
    }
}
