package com.novabank.auth.infrastructure.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI authenticationOpenApi(){
        return new OpenAPI()
            .info(
                new Info()
                    .title("NovaBank Authentication Service")
                    .description("Authentication and user management APIs.")
                    .version("v1.0.0")
                    .contact(
                        new Contact()
                            .name("NovaBank Engineering")
                            .email("baldev@example.com")
                    )
            );
    }

}
