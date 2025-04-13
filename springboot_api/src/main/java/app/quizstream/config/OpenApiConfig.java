package app.quizstream.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.core.env.Environment;

@Configuration
public class OpenApiConfig {

    private final Environment environment;

    public OpenApiConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("QuizStream API")
                        .description("QuizStream API Documentation")
                        .version("v0.1"));
    }

}
