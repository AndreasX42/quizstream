package app.quizstream.security.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EnvConfigs {

    public String backendAddress;
    public String backendCreateNewQuizEndpoint;

    public final int TOKEN_EXPIRATION = 7200000; // 2 hours.
    public final String BEARER_PREFIX = "Bearer ";
    public final String AUTHORIZATION = "Authorization";
    public final String REGISTER_PATH = "/users/register";
    public final String AUTH_PATH_FRONTEND = "/users/login";
    public final String AUTH_PATH_API = "/users/authenticate";

    @PostConstruct
    public void init() {
        backendAddress = String.format("http://%s:%s", backendHost, backendPort);
        backendCreateNewQuizEndpoint = backendAddress + "/quizzes/new";
    }

    public String getAppHost() {
        return appHost;
    }

    public String getSqsQueueUrl() {
        return sqsQueueUrl;
    }

    @Value("${app.host:http://localhost:4200}")
    private String appHost;

    @Value("${backend.host:localhost}")
    private String backendHost;

    @Value("${backend.port:8080}")
    private String backendPort;

    @Value("${aws.sqs.queue.url}")
    private String sqsQueueUrl;
}
