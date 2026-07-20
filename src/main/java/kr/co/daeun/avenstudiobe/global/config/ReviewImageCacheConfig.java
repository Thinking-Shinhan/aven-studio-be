package kr.co.daeun.avenstudiobe.global.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReviewImageCacheConfig {

    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

    @Bean("reviewImageCache")
    public Cache<String, byte[]> reviewImageCache(
            @Value("${review.image-cache.max-weight-mb:64}") long maxWeightMegabytes,
            @Value("${review.image-cache.expire-after-access-minutes:60}") long expireAfterAccessMinutes
    ) {
        long maximumWeight = positiveOrDefault(maxWeightMegabytes, 64) * BYTES_PER_MEGABYTE;
        Duration expiration = Duration.ofMinutes(positiveOrDefault(expireAfterAccessMinutes, 60));

        return Caffeine.newBuilder()
                .maximumWeight(maximumWeight)
                .weigher((String key, byte[] value) -> Math.max(1, value.length))
                .expireAfterAccess(expiration)
                .build();
    }

    private long positiveOrDefault(long value, long defaultValue) {
        return value <= 0 ? defaultValue : value;
    }
}
