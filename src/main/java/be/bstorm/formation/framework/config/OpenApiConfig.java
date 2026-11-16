package be.bstorm.formation.framework.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentation OpenAPI / Swagger UI — désactivée en prod ({@code springdoc.api-docs.enabled=false}). */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfig {

    @Bean
    OpenAPI formationOpenApi(@Value("${spring.application.name}") String name) {
        return new OpenAPI().info(new Info()
                .title(name + " API")
                .version("v1")
                .description("API REST Formation"));
    }
}
