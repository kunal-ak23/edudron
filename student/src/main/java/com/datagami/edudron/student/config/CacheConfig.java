package com.datagami.edudron.student.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            "courseAnalytics",
            "lectureAnalytics",
            "sectionAnalytics",
            "classAnalytics",
            "examFromContent"      // Cached exam from Content service (1 min TTL, 2000 max for scale)
        );
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .recordStats()
        );
        return cacheManager;
    }
}
