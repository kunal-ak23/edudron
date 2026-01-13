package com.datagami.edudron.gateway.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class CustomHealthIndicator implements HealthIndicator {

    @Value("${spring.application.name:gateway}")
    private String serviceName;

    @Value("${server.port:8080}")
    private String serverPort;

    @Override
    public Health health() {
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("service", serviceName);
            details.put("version", getClass().getPackage().getImplementationVersion() != null ? 
                getClass().getPackage().getImplementationVersion() : "1.0.0");
            details.put("server", getServerName());
            details.put("port", serverPort);
            details.put("timestamp", OffsetDateTime.now().toString());
            details.put("uptime", getUptime());
            details.put("jvm", getJvmInfo());
            details.put("environment", getEnvironment());
            
            return Health.up()
                .withDetails(details)
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }

    private String getServerName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getUptime() {
        long uptime = System.currentTimeMillis() - getStartTime();
        long seconds = uptime / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return String.format("%dd %dh %dm %ds", days, hours % 24, minutes % 60, seconds % 60);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    private long getStartTime() {
        return ManagementFactory.getRuntimeMXBean().getStartTime();
    }

    private Map<String, Object> getJvmInfo() {
        Map<String, Object> jvmInfo = new HashMap<>();
        Runtime runtime = Runtime.getRuntime();
        
        jvmInfo.put("javaVersion", System.getProperty("java.version"));
        jvmInfo.put("javaVendor", System.getProperty("java.vendor"));
        jvmInfo.put("maxMemory", runtime.maxMemory());
        jvmInfo.put("totalMemory", runtime.totalMemory());
        jvmInfo.put("freeMemory", runtime.freeMemory());
        jvmInfo.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
        jvmInfo.put("availableProcessors", runtime.availableProcessors());
        
        return jvmInfo;
    }

    private String getEnvironment() {
        String profile = System.getProperty("spring.profiles.active");
        if (profile == null || profile.isEmpty()) {
            profile = "default";
        }
        return profile;
    }
}

