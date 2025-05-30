package app.quizstream.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import app.quizstream.dto.quiz.QuizCreateRequestDto;
import app.quizstream.dto.quiz.QuizCreateResultDto;
import app.quizstream.entity.request.QuizRequest;
import app.quizstream.security.config.EnvConfigs;
import app.quizstream.util.mapper.QuizMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

@Service
@Profile("!prod")
public class QuizCreationHttp implements IQuizCreationInitiator {

    private static final Logger logger = LoggerFactory.getLogger(QuizCreationHttp.class);

    private final RestTemplate restTemplate;
    private final EnvConfigs envConfigs;
    private final QuizRequestService quizJobService;
    private final QuizMapper quizMapper;
    private final ObjectMapper objectMapper;

    public QuizCreationHttp(RestTemplate restTemplate, EnvConfigs envConfigs,
            QuizRequestService quizJobService, QuizMapper quizMapper, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.envConfigs = envConfigs;
        this.quizJobService = quizJobService;
        this.quizMapper = quizMapper;
        this.objectMapper = objectMapper;
    }

    @Async
    public void createQuiz(QuizCreateRequestDto quizCreateDto, QuizRequest quizJob) {

        // create the request body for FastAPI
        Map<String, Object> body = Map.of(
                "user_id", quizCreateDto.userId(),
                "quiz_name", quizCreateDto.quizName(),
                "api_keys", quizCreateDto.apiKeys(),
                "youtube_url", quizCreateDto.videoUrl(),
                "language", quizCreateDto.language(),
                "type", quizCreateDto.type()
                        .toString(),
                "difficulty", quizCreateDto.difficulty()
                        .toString());

        // make the POST request to the backend in the background
        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, new HttpHeaders());

            ResponseEntity<String> response = restTemplate.exchange(
                    envConfigs.backendCreateNewQuizEndpoint,
                    HttpMethod.POST,
                    request,
                    String.class);

            // TODO: should quizDto be returned somehow?
            QuizCreateResultDto quizDto = quizMapper.convertToQuizOutboundDto(response.getBody());

            // if quiz was created successfully update the QuizJob state
            quizJob.updateRequestState(QuizRequest.Status.FINISHED, quizDto.quizId(), null, null);

        } catch (Exception e) {

            String errorInt = "";
            String errorExt = "";

            if (e instanceof HttpClientErrorException httpError) {

                Optional<String[]> errors = handleHttpClientBackendError(httpError);

                if (errors.isPresent()) {
                    errorInt = errors.get()[0];
                    errorExt = errors.get()[1];
                }

            } else {
                errorInt = e.getMessage();
                errorExt = "Unexpected error processing request. Please try again later.";
            }

            // update QuizJob state to FAILED if there is an error
            quizJob.updateRequestState(QuizRequest.Status.FAILED, null, errorInt, errorExt);
        } finally {
            // save the updated QuizJob state
            quizJobService.updateQuizRequest(quizJob);

            logger.info("{} creating quiz '{}' for user with id '{}', '{}', '{}', '{}'.",
                    quizJob.getStatus()
                            .name(),
                    quizCreateDto.quizName(), quizCreateDto.userId(), quizCreateDto.language()
                            .name(),
                    quizCreateDto.difficulty()
                            .name(),
                    quizCreateDto.type()
                            .name());
        }

    }

    @Override
    public void initiate(QuizCreateRequestDto quizCreateDto, QuizRequest quizJob) {
        logger.info("Initiating quiz creation via async backend call for quizJobId: {}", quizJob.getId());
        this.createQuiz(quizCreateDto, quizJob);
    }

    public Optional<String[]> handleHttpClientBackendError(HttpClientErrorException e) {
        try {
            String responseBody = e.getResponseBodyAsString();

            Map<String, Object> errorDetails = objectMapper.readValue(responseBody, Map.class);

            logger.error("Error details: {}", errorDetails);

            Map<String, Object> detail = (Map<String, Object>) errorDetails.get("detail");
            String errorInternal = (String) detail.get("error_internal");
            String errorExternal = (String) detail.get("error_external");

            return Optional.of(new String[] { errorInternal, errorExternal });

        } catch (Exception parseException) {
            parseException.printStackTrace();
        }

        return Optional.empty();
    }
}
