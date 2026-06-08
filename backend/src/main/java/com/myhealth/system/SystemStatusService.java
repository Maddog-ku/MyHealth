package com.myhealth.system;

import com.myhealth.ai.AiProvider;
import com.myhealth.config.RateLimitProperties;
import com.myhealth.system.SystemDtos.ComponentStatus;
import com.myhealth.system.SystemDtos.SystemStatusResponse;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;

@Service
public class SystemStatusService {
    private static final String UP = "UP";
    private static final String DOWN = "DOWN";
    private static final String DEGRADED = "DEGRADED";

    private final DataSource dataSource;
    private final AiProvider aiProvider;
    private final RateLimitProperties rateLimitProperties;

    public SystemStatusService(DataSource dataSource, AiProvider aiProvider,
                               RateLimitProperties rateLimitProperties) {
        this.dataSource = dataSource;
        this.aiProvider = aiProvider;
        this.rateLimitProperties = rateLimitProperties;
    }

    public SystemStatusResponse status() {
        List<ComponentStatus> components = new ArrayList<>();
        components.add(new ComponentStatus("backend", "Backend API", UP, "request handled"));
        components.add(databaseStatus());
        components.add(aiStatus());
        components.add(rateLimitStatus());

        boolean databaseDown = components.stream()
                .anyMatch(component -> "database".equals(component.key()) && DOWN.equals(component.status()));
        boolean anyDown = components.stream().anyMatch(component -> DOWN.equals(component.status()));
        String overall = databaseDown ? DOWN : anyDown ? DEGRADED : UP;
        return new SystemStatusResponse(overall, Instant.now(), components);
    }

    private ComponentStatus databaseStatus() {
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(2);
            return new ComponentStatus("database", "Database", valid ? UP : DOWN,
                    valid ? "connection validated" : "connection failed validation");
        } catch (Exception ex) {
            return new ComponentStatus("database", "Database", DOWN, safeMessage(ex));
        }
    }

    private ComponentStatus aiStatus() {
        String detail = "%s text=%s vision=%s%s".formatted(
                aiProvider.provider(),
                aiProvider.textModel(),
                aiProvider.visionModel(),
                aiProvider.loaded() ? " loaded" : " idle");
        return new ComponentStatus("ai", "AI provider", UP, detail);
    }

    private ComponentStatus rateLimitStatus() {
        if (rateLimitProperties.getBackend() == RateLimitProperties.Backend.MEMORY) {
            return new ComponentStatus("rateLimit", "Rate limiter", UP, "memory backend");
        }

        RedisURI redisUri = RedisURI.create(rateLimitProperties.getRedis().getUri());
        redisUri.setTimeout(rateLimitProperties.getRedis().getRequestTimeout());
        RedisClient client = RedisClient.create(redisUri);
        try (StatefulRedisConnection<String, String> connection = client.connect()) {
            String pong = connection.sync().ping();
            boolean ok = "PONG".equalsIgnoreCase(pong);
            return new ComponentStatus("rateLimit", "Rate limiter", ok ? UP : DOWN,
                    ok ? "redis backend reachable" : "redis ping failed");
        } catch (Exception ex) {
            return new ComponentStatus("rateLimit", "Rate limiter", DOWN, safeMessage(ex));
        } finally {
            client.shutdown();
        }
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
}
