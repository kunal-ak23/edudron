package com.datagami.edudron.gateway;

import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

/**
 * Health check endpoint using WebFlux RouterFunction.
 * Note: Spring Actuator already provides /actuator/health, but this provides a simpler /healthz endpoint.
 */
@Configuration
public class HealthController {
    
    @Bean
    public RouterFunction<ServerResponse> healthzRoute(HealthEndpoint healthEndpoint) {
        return route()
                .GET("/healthz", request -> 
                    ok().bodyValue(healthEndpoint.health())
                )
                .build();
    }
}

