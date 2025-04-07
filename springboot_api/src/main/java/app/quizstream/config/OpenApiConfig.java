package app.quizstream.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("QuizStream API")
                        .description("QuizStream API Documentation")
                        .version("v0.1"))
                .servers(List.of(
                        new Server().url("/api/v1")
                                .description("Production server")));
    }

}
