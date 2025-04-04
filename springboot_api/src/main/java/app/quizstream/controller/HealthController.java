package app.quizstream.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;

@RestController
public class HealthController {

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${backend.host:localhost}")
    private String backendHost;

    @Value("${backend.port:8080}")
    private String backendPort;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        String fastApiUrl = String.format("http://%s:%s/health", backendHost, backendPort);

        try {
            // Check FastAPI health
            String fastApiResponse = restTemplate.getForObject(fastApiUrl, String.class);

            // FastAPI returns {"message": "Ok"}
            if (fastApiResponse == null || !fastApiResponse.contains("\"message\":\"Ok\"")) {
                throw new RuntimeException("FastAPI health check failed");
            }

            logger.info("Health check passed - FastAPI response: {}", fastApiResponse);
            return ResponseEntity.ok("Ok");
        } catch (Exception e) {
            logger.error("FastAPI health check failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("FastAPI service unhealthy");
        }
    }
}
