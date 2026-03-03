package com.datagami.edudron.coreapi.config;

import liquibase.integration.spring.SpringLiquibase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class LiquibaseConfig {

    @Bean
    public SpringLiquibase identityLiquibase(DataSource dataSource) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/identity-master.yaml");
        liquibase.setDefaultSchema("idp");
        liquibase.setLiquibaseSchema("public");
        return liquibase;
    }

    @Bean
    public SpringLiquibase studentLiquibase(DataSource dataSource) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/student-master.yaml");
        liquibase.setDefaultSchema("student");
        liquibase.setLiquibaseSchema("public");
        return liquibase;
    }

    @Bean
    public SpringLiquibase paymentLiquibase(DataSource dataSource) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/payment-master.yaml");
        liquibase.setDefaultSchema("payment");
        liquibase.setLiquibaseSchema("public");
        return liquibase;
    }
}
