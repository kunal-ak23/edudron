package com.datagami.edudron.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("EduDron API")
                        .description("Learning Management System API Documentation")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("EduDron Team")
                                .email("support@edudron.com")
                                .url("https://edudron.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Gateway Server"),
                        new Server().url("http://localhost:8081").description("Identity Service"),
                        new Server().url("http://localhost:8082").description("Content Service"),
                        new Server().url("http://localhost:8083").description("Student Service"),
                        new Server().url("http://localhost:8084").description("Payment Service")
                ))
                .components(new Components()
                        .addSecuritySchemes("X-Client-Id", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Client-Id")
                                .description("Client ID for multi-tenancy")))
                .addSecurityItem(new SecurityRequirement().addList("X-Client-Id"));
    }
}

