package com.datagami.edudron.coreapi;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(exclude = {
    LiquibaseAutoConfiguration.class
})
@ComponentScan(
    basePackages = {
        "com.datagami.edudron.coreapi",
        "com.datagami.edudron.identity",
        "com.datagami.edudron.student",
        "com.datagami.edudron.payment",
        "com.datagami.edudron.common"
    },
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
            // Main classes
            com.datagami.edudron.identity.IdentityApplication.class,
            com.datagami.edudron.student.StudentApplication.class,
            com.datagami.edudron.payment.PaymentApplication.class,
            // SecurityConfigs (conflicting @Bean filterChain)
            com.datagami.edudron.identity.config.SecurityConfig.class,
            com.datagami.edudron.student.config.SecurityConfig.class,
            com.datagami.edudron.payment.config.SecurityConfig.class,
            // JwtFilter + JwtUtil (keep identity's, exclude others)
            com.datagami.edudron.student.security.JwtAuthenticationFilter.class,
            com.datagami.edudron.payment.security.JwtAuthenticationFilter.class,
            com.datagami.edudron.student.security.JwtUtil.class,
            com.datagami.edudron.payment.security.JwtUtil.class,
            // AsyncConfigs (conflicting @Bean eventTaskExecutor)
            com.datagami.edudron.identity.config.AsyncConfig.class,
            com.datagami.edudron.student.config.AsyncConfig.class,
            com.datagami.edudron.payment.config.AsyncConfig.class,
            // CacheConfigs (conflicting @Bean cacheManager)
            com.datagami.edudron.identity.config.CacheConfig.class,
            com.datagami.edudron.student.config.CacheConfig.class,
        }
    )
)
@EntityScan(basePackages = {
    "com.datagami.edudron.identity.domain",
    "com.datagami.edudron.identity.entity",
    "com.datagami.edudron.student.domain",
    "com.datagami.edudron.payment.domain",
    "com.datagami.edudron.common.domain"
})
@EnableJpaRepositories(basePackages = {
    "com.datagami.edudron.identity.repo",
    "com.datagami.edudron.student.repo",
    "com.datagami.edudron.payment.repo"
})
public class CoreApiApplication {
    private static final Logger logger = LoggerFactory.getLogger(CoreApiApplication.class);

    public static void main(String[] args) {
        loadEnvFile();
        // Shared changelog files (e.g. db.changelog-0000-common-audit.yaml) exist in multiple
        // library JARs. They are identical — tell Liquibase to warn instead of fail on duplicates.
        System.setProperty("liquibase.duplicateFileMode", "WARN");
        SpringApplication.run(CoreApiApplication.class, args);
    }

    private static void loadEnvFile() {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .filename(".env")
                    .directory(".")
                    .ignoreIfMissing()
                    .load();

            if (dotenv == null || dotenv.entries().isEmpty()) {
                dotenv = Dotenv.configure()
                        .filename(".env")
                        .directory("..")
                        .ignoreIfMissing()
                        .load();
            }

            if (dotenv != null && !dotenv.entries().isEmpty()) {
                int loadedCount = 0;
                for (var entry : dotenv.entries()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    if (value != null && !value.isEmpty() && System.getProperty(key) == null) {
                        System.setProperty(key, value);
                        loadedCount++;
                    }
                }
                logger.info("Loaded {} environment variables from .env file", loadedCount);
            }
        } catch (Exception e) {
            logger.warn("Could not load .env file: {}", e.getMessage());
        }
    }
}
