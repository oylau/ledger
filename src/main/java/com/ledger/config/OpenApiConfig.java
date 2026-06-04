package com.ledger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ledgerOpenAPI() {
        return new OpenAPI().info(new Info().title("Ledger API")
                .description("In-memory thread-safe ledger service. Supports deposits, withdrawals, "
                        + "temporal balance queries, and transaction history.")
                .version("0.0.1-SNAPSHOT")
                .contact(new Contact().name("io.github.irenelau-gds").url("https://github.com/irenelau-gds/ledger"))
                .license(new License().name("MIT")));
    }
}
