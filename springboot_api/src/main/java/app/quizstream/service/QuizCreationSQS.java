package app.quizstream.service;

import app.quizstream.dto.quiz.QuizCreateRequestDto;
import app.quizstream.entity.request.QuizRequest;
import app.quizstream.repository.QuizRequestRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import app.quizstream.security.config.EnvConfigs;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.util.Map;

@Service
@Profile("prod")
public class QuizCreationSQS implements IQuizCreationInitiator {

    private static final Logger logger = LoggerFactory.getLogger(QuizCreationSQS.class);

    private final EnvConfigs envConfigs;
    private final ObjectMapper objectMapper;
    private final SqsClient sqsClient;
    private final QuizRequestRepository quizRequestRepository;

    public QuizCreationSQS(EnvConfigs envConfigs, ObjectMapper objectMapper, SqsClient sqsClient,
            QuizRequestRepository quizRequestRepository) {
        this.envConfigs = envConfigs;
        this.objectMapper = objectMapper;
        this.sqsClient = sqsClient;
        this.quizRequestRepository = quizRequestRepository;
    }

    public void publishQuizRequestToQueue(QuizCreateRequestDto quizCreateDto, QuizRequest quizJob) {

        // create the message body for SQS
        Map<String, Object> messageData = Map.of(
                "user_id", quizCreateDto.userId(),
                "quiz_name", quizCreateDto.quizName(),
                "api_keys", quizCreateDto.apiKeys(),
                "youtube_url", quizCreateDto.videoUrl(),
                "language", quizCreateDto.language().toString(),
                "type", quizCreateDto.type().toString(),
                "difficulty", quizCreateDto.difficulty().toString());

        String messageBodyJson;
        try {
            messageBodyJson = objectMapper.writeValueAsString(messageData);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize quiz request data");
            quizJob.updateRequestState(QuizRequest.Status.FAILED, null, null,
                    "Failed to serialize quiz request data");
            quizRequestRepository.save(quizJob);
            return;
        }

        logger.info("QuizJob: {}", quizJob);
        logger.info("QuizJob User: {}", quizJob.getUser());
        logger.info("QuizJob User ID: {}", quizJob.getUser().getId());
        logger.info("QuizJob ID: {}", quizJob.getId().toString());

        try {
            SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                    .queueUrl(envConfigs.getSqsQueueUrl())
                    .messageBody(messageBodyJson)
                    .messageGroupId(quizJob.getUser().getId().toString())
                    .messageDeduplicationId(quizJob.getId().toString())
                    .build();

            sqsClient.sendMessage(sendMsgRequest);

            logger.info("Successfully sent quiz creation request to SQS queue {} for quizJobId: {}",
                    envConfigs.getSqsQueueUrl(), quizJob.getId());

            quizJob.updateRequestState(QuizRequest.Status.QUEUED, null, null,
                    "Queued request");
            quizRequestRepository.save(quizJob);

        } catch (SqsException e) {
            logger.error("Failed to send quiz request message to SQS queue {} for quizJobId: {}. AWS Error: {}",
                    envConfigs.getSqsQueueUrl(), quizJob.getId(),
                    e.awsErrorDetails().errorMessage(), e);
            quizJob.updateRequestState(QuizRequest.Status.FAILED, null, null,
                    "Failed to queue request");
            quizRequestRepository.save(quizJob);

        } catch (Exception e) {
            logger.error("An unexpected error occurred while sending quiz request to SQS for quizJobId: {}. Error: {}",
                    quizJob.getId(), e.getMessage(), e);
            quizJob.updateRequestState(QuizRequest.Status.FAILED, null, null,
                    "Unexpected error while queuing request");
            quizRequestRepository.save(quizJob);
        }
    }

    @Override
    public void initiate(QuizCreateRequestDto quizCreateDto, QuizRequest quizJob) {
        logger.info("Initiating quiz creation via SQS queue for quizJobId: {}", quizJob.getId());
        this.publishQuizRequestToQueue(quizCreateDto, quizJob);
    }
}
