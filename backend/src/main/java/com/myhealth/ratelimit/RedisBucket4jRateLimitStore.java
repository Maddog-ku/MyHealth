package com.myhealth.ratelimit;

import com.myhealth.common.ApiException;
import com.myhealth.common.ErrorCode;
import com.myhealth.config.RateLimitProperties;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.rate-limit.backend", havingValue = "redis")
public class RedisBucket4jRateLimitStore implements RateLimitStore {
    private static final Logger log = LoggerFactory.getLogger(RedisBucket4jRateLimitStore.class);

    private final RateLimitProperties properties;
    private volatile RedisClient redisClient;
    private volatile StatefulRedisConnection<String, byte[]> connection;
    private volatile ProxyManager<String> proxyManager;

    public RedisBucket4jRateLimitStore(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public void check(String key, int limit, Duration window) {
        try {
            Bucket bucket = proxyManager().getProxy(redisKey(key), () -> configuration(limit, window));
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                throw rateLimited();
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (properties.getRedis().isFailOpen()) {
                log.warn("Redis rate limiter unavailable; allowing request. cause={}", ex.toString());
                return;
            }
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE,
                    "Rate limiter unavailable");
        }
    }

    private ProxyManager<String> proxyManager() {
        ProxyManager<String> current = proxyManager;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (proxyManager == null) {
                RedisClient client = RedisClient.create(properties.getRedis().getUri());
                try {
                    StatefulRedisConnection<String, byte[]> redisConnection = client.connect(
                            RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
                    ProxyManager<String> manager = Bucket4jLettuce.casBasedBuilder(redisConnection)
                            .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                                    properties.getRedis().getTtlPadding()))
                            .requestTimeout(properties.getRedis().getRequestTimeout())
                            .maxRetries(5)
                            .build();
                    redisClient = client;
                    connection = redisConnection;
                    proxyManager = manager;
                } catch (RuntimeException ex) {
                    client.shutdown();
                    throw ex;
                }
            }
            return proxyManager;
        }
    }

    private BucketConfiguration configuration(int limit, Duration window) {
        return BucketConfiguration.builder()
                .addLimit(bandwidth -> bandwidth.capacity(limit).refillGreedy(limit, window))
                .build();
    }

    private String redisKey(String key) {
        return "%s:%s".formatted(properties.getRedis().getKeyPrefix(), key);
    }

    private ApiException rateLimited() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED,
                "Too many requests. Please retry later.");
    }

    @PreDestroy
    void close() {
        StatefulRedisConnection<String, byte[]> currentConnection = connection;
        if (currentConnection != null) {
            currentConnection.close();
        }
        RedisClient currentClient = redisClient;
        if (currentClient != null) {
            currentClient.shutdown();
        }
    }
}
