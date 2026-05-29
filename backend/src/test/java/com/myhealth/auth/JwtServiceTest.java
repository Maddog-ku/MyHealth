package com.myhealth.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.myhealth.config.AppProperties;
import com.myhealth.user.AppUser;
import com.myhealth.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService service;
    private AppProperties.Jwt jwtProps;

    @BeforeEach
    void setUp() {
        jwtProps = new AppProperties.Jwt("test-secret-test-secret-test-secret-32bytes!!", 15, 30);
        AppProperties props = new AppProperties(
                jwtProps,
                new AppProperties.Cors(List.of("http://localhost")),
                new AppProperties.Ai("local", "http://localhost", "x", "y", 300),
                "./uploads");
        service = new JwtService(props);
    }

    private UserPrincipal principal(long id, String email) {
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(Role.USER);
        setField(u, "id", id);
        return new UserPrincipal(u);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    void issuedToken_canBeParsed_andCarriesSubjectAndUid() {
        String token = service.issueAccessToken(principal(42L, "alice@example.com"));

        Claims claims = service.parse(token);

        assertThat(claims.getSubject()).isEqualTo("alice@example.com");
        assertThat(claims.get("uid", Integer.class).longValue()).isEqualTo(42L);
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void expiresInSeconds_matchesConfiguredAccessTtl() {
        assertThat(service.expiresInSeconds()).isEqualTo(Duration.ofMinutes(15).toSeconds());
    }

    @Test
    void parse_rejectsTokenSignedWithDifferentSecret() {
        AppProperties otherProps = new AppProperties(
                new AppProperties.Jwt("OTHER-secret-OTHER-secret-OTHER-secret-32bytes!", 15, 30),
                new AppProperties.Cors(List.of("http://localhost")),
                new AppProperties.Ai("local", "http://localhost", "x", "y", 300),
                "./uploads");
        JwtService otherService = new JwtService(otherProps);
        String foreignToken = otherService.issueAccessToken(principal(1L, "x@y.z"));

        assertThatThrownBy(() -> service.parse(foreignToken))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void parse_rejectsExpiredToken() {
        // ttl = 0 minutes → token is already expired the moment it is issued.
        AppProperties expiredProps = new AppProperties(
                new AppProperties.Jwt("test-secret-test-secret-test-secret-32bytes!!", 0, 30),
                new AppProperties.Cors(List.of("http://localhost")),
                new AppProperties.Ai("local", "http://localhost", "x", "y", 300),
                "./uploads");
        JwtService expiredService = new JwtService(expiredProps);
        String token = expiredService.issueAccessToken(principal(1L, "x@y.z"));

        assertThatThrownBy(() -> expiredService.parse(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void parse_rejectsTamperedPayload() {
        String token = service.issueAccessToken(principal(1L, "x@y.z"));
        String[] parts = token.split("\\.");
        // Swap a character in the payload section to invalidate the HMAC.
        String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "AA" + "." + parts[2];

        assertThatThrownBy(() -> service.parse(tampered))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }
}
