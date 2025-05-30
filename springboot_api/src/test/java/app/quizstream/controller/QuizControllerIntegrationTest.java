package app.quizstream.controller;

import app.quizstream.dto.quiz.*;
import app.quizstream.entity.User;
import app.quizstream.entity.UserQuiz;
import app.quizstream.entity.embedding.LangchainPGEmbedding;
import app.quizstream.entity.request.QuizRequest;
import app.quizstream.repository.*;
import app.quizstream.security.config.EnvConfigs;
import app.quizstream.service.QuizRequestService;
import app.quizstream.service.UserQuizService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.escoffier.loom.loomunit.LoomUnitExtension;
import me.escoffier.loom.loomunit.ShouldNotPin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.Commit;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import app.quizstream.dto.user.UserRegisterDto;
import app.quizstream.dto.user.UserOutboundDto;
import app.quizstream.service.UserService;

@Commit
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ExtendWith(LoomUnitExtension.class)
@ShouldNotPin
@ActiveProfiles("integration-test")
public class QuizControllerIntegrationTest {

        @Value("${default.openai.api.key}")
        private String apiKeyTest;

        private final UserQuizRepository userQuizRepository;
        private final LangchainPGCollectionRepository langchainPGCollectionRepository;
        private final LangchainPGEmbeddingRepository langchainPGEmbeddingRepository;
        private final QuizRequestService quizRequestService;
        private final UserQuizService userQuizService;
        private final UserService userService;
        private final EnvConfigs envConfigs;
        private final ObjectMapper objectMapper;
        private final MockMvc mockMvc;

        @Autowired
        public QuizControllerIntegrationTest(UserQuizRepository userQuizRepository,
                        LangchainPGCollectionRepository langchainPGCollectionRepository,
                        LangchainPGEmbeddingRepository langchainPGEmbeddingRepository,
                        QuizRequestService quizRequestService, UserQuizService userQuizService,
                        UserService userService, EnvConfigs envConfigs, ObjectMapper objectMapper,
                        MockMvc mockMvc) {
                this.userQuizRepository = userQuizRepository;
                this.langchainPGCollectionRepository = langchainPGCollectionRepository;
                this.langchainPGEmbeddingRepository = langchainPGEmbeddingRepository;
                this.quizRequestService = quizRequestService;
                this.userQuizService = userQuizService;
                this.userService = userService;
                this.envConfigs = envConfigs;
                this.objectMapper = objectMapper;
                this.mockMvc = mockMvc;
        }

        private User testUser;
        private UUID createdQuizId;
        private QuizOutboundDto quizOutboundData;
        private QuizCreateRequestDto quizCreateRequestDto;

        Predicate<Optional<QuizRequest>> isPresentAndTerminated = quizRequest -> quizRequest.isPresent()
                        && quizRequest.get()
                                        .getStatus() != QuizRequest.Status.CREATING;

        @BeforeAll
        public void setUp() {
                setUpUserAccount();
        }

        private void setUpUserAccount() {
                UUID userId = UUID.randomUUID();
                String userName = "John_Doe_2";
                this.testUser = new User();
                this.testUser.setId(userId);
                this.testUser.setUsername(userName);
                this.testUser.setEmail(userName + "@mail.com");

                this.quizCreateRequestDto = new QuizCreateRequestDto(testUser.getId(), userName,
                                "my first quiz",
                                "https://www.youtube.com/watch?v=IFx8eABfivg",
                                Map.of("OPENAI_API_KEY", apiKeyTest),
                                UserQuiz.Language.EN,
                                UserQuiz.Type.MULTIPLE_CHOICE,
                                UserQuiz.Difficulty.HARD);
        }

        @Test
        @Order(1)
        public void testRegisterUser_whenValidUserDetailsProvided_shouldRegisterUserSuccessfully()
                        throws Exception {

                UserRegisterDto registerRequestDto = new UserRegisterDto(testUser.getId(), testUser.getUsername(),
                                testUser.getEmail());
                String registerRequestDtoJson = objectMapper.writeValueAsString(registerRequestDto);

                MockHttpServletResponse response = mockMvc
                                .perform(MockMvcRequestBuilders.post(envConfigs.REGISTER_PATH)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(registerRequestDtoJson))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse();

                // check login response dto
                UserOutboundDto userOutboundDto = objectMapper.readValue(response.getContentAsString(),
                                UserOutboundDto.class);

                assertThat(userOutboundDto).isNotNull();
                assertThat(userOutboundDto.id()).isEqualTo(testUser.getId());
                assertThat(userOutboundDto.username()).isEqualTo(testUser.getUsername());
                assertThat(userOutboundDto.email()).isEqualTo(testUser.getEmail());

        }

        @Test
        @Order(2)
        public void testCreateQuiz_whenValidQuizDataProvided_shouldCreateQuizSuccessfully() throws Exception {

                String quizCreateRequestDtoJson = objectMapper.writeValueAsString(quizCreateRequestDto);

                String response = mockMvc.perform(MockMvcRequestBuilders.post("/users/{userId}/quizzes",
                                testUser.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(quizCreateRequestDtoJson))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                // check response from endpoint
                QuizRequestDto quizCreateResponseDto = objectMapper.readValue(response, QuizRequestDto.class);

                // check that user was created
                assertThat(userService.getByIdOptional(testUser.getId()).isPresent()).isTrue();

                assertThat(quizCreateResponseDto).isNotNull();
                assertThat(quizCreateResponseDto.userId()).isEqualTo(testUser.getId());
                assertThat(quizCreateResponseDto.quizName()).isEqualTo(quizCreateRequestDto.quizName());
                assertThat(quizCreateResponseDto.dateCreated()).isNotNull();
                assertThat(quizCreateResponseDto.errorMessage()).isNull();
                assertThat(quizCreateResponseDto.metadata()
                                .videoUrl()).isEqualTo(quizCreateRequestDto.videoUrl());
                assertThat(quizCreateResponseDto.metadata()
                                .language()).isEqualTo(quizCreateRequestDto.language());
                assertThat(quizCreateResponseDto.metadata()
                                .difficulty()).isEqualTo(quizCreateRequestDto.difficulty());
                assertThat(quizCreateResponseDto.metadata()
                                .type()).isEqualTo(quizCreateRequestDto.type());

                // await the creation of quiz
                await()
                                .atMost(120, TimeUnit.SECONDS)
                                .pollInterval(2, TimeUnit.SECONDS)
                                .until(() -> isPresentAndTerminated.test(quizRequestService.getRequestByQuizRequestId(
                                                testUser.getId(), quizCreateRequestDto.quizName())));

                // check that QuizRequest was created in quiz_requests table
                QuizRequest quizRequestRow = quizRequestService
                                .getRequestByQuizRequestId(testUser.getId(), quizCreateRequestDto.quizName())
                                .orElseThrow();

                assertThat(quizRequestRow.getStatus()).isEqualTo(QuizRequest.Status.FINISHED);
                assertThat(quizRequestRow.getId()
                                .getUserId()).isEqualTo(quizCreateRequestDto.userId());
                assertThat(quizRequestRow.getId()
                                .getQuizName()).isEqualTo(quizCreateRequestDto.quizName());
                assertThat(quizRequestRow.getStatus()).isEqualTo(QuizRequest.Status.FINISHED);
                assertThat(quizRequestRow.getQuizId()).isNotNull();
                assertThat(quizRequestRow.getDateCreated()).isCloseTo(quizCreateResponseDto.dateCreated(),
                                within(1, ChronoUnit.SECONDS));
                assertThat(quizRequestRow.getDateModified()).isAfter(quizRequestRow.getDateCreated());
                assertThat(quizRequestRow.getMessageInternal()).isNull();
                assertThat(quizRequestRow.getMessageExternal()).isNull();

                // check that UserQuiz was created in user_quiz table
                UserQuiz userQuizRow = userQuizService.getByUserQuizId(testUser.getId(), quizRequestRow.getQuizId());
                assertThat(userQuizRow.getId()
                                .getUserId()).isEqualTo(testUser.getId());
                assertThat(userQuizRow.getId()
                                .getQuizId()).isEqualTo(quizRequestRow.getQuizId());
                assertThat(userQuizRow.getDateCreated()).isBetween(quizRequestRow.getDateCreated(),
                                quizRequestRow.getDateModified());
                assertThat(userQuizRow.getNumQuestions()).isGreaterThan(0);
                assertThat(userQuizRow.getNumTries()).isEqualTo(0);
                assertThat(userQuizRow.getNumCorrect()).isEqualTo(0);
                assertThat(userQuizRow.getLanguage()).isEqualTo(quizCreateRequestDto.language());
                assertThat(userQuizRow.getType()).isEqualTo(quizCreateRequestDto.type());
                assertThat(userQuizRow.getDifficulty()).isEqualTo(quizCreateRequestDto.difficulty());

                // store quiz id for later use
                this.createdQuizId = userQuizRow.getId()
                                .getQuizId();
        }

        @Test
        @Order(3)
        public void testGetQuizByUserQuizId_whenUserQuizIdValid_shouldFetchQuiz() throws Exception {

                String response = mockMvc
                                .perform(MockMvcRequestBuilders
                                                .get("/users/{userId}/quizzes/{quizId}", testUser.getId(),
                                                                createdQuizId)
                                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                QuizOutboundDto quizOutboundDto = objectMapper.readValue(response, QuizOutboundDto.class);

                assertThat(quizOutboundDto).isNotNull();
                assertThat(quizOutboundDto.userId()).isEqualTo(testUser.getId());
                assertThat(quizOutboundDto.quizName()).isEqualTo(quizCreateRequestDto.quizName());
                assertThat(quizOutboundDto.quizId()).isEqualTo(createdQuizId);

                assertThat(quizOutboundDto.dateCreated()).isEqualTo(LocalDate.now()
                                .toString());
                assertThat(quizOutboundDto.numTries()).isEqualTo(0);
                assertThat(quizOutboundDto.numCorrect()).isEqualTo(0);

                assertThat(quizOutboundDto.numQuestions()).isGreaterThan(0);
                assertThat(quizOutboundDto.language()).isEqualTo(UserQuiz.Language.EN);
                assertThat(quizOutboundDto.type()).isEqualTo(UserQuiz.Type.MULTIPLE_CHOICE);
                assertThat(quizOutboundDto.difficulty()).isEqualTo(UserQuiz.Difficulty.HARD);

                var videoMetadata = quizOutboundDto.metadata();
                assertThat(videoMetadata.title()).hasSizeGreaterThan(0);
                assertThat(videoMetadata.videoUrl()).isEqualTo("IFx8eABfivg");
                assertThat(videoMetadata.thumbnailUrl()).isEqualTo("https://i.ytimg.com/vi/IFx8eABfivg/hq720.jpg");
                assertThat(videoMetadata.description()).hasSizeGreaterThan(10);
                assertThat(videoMetadata.viewers()).isGreaterThan(0);
                assertThat(videoMetadata.publishDate()).isInThePast();
                assertThat(videoMetadata.viewers()).isGreaterThan(0);
                assertThat(videoMetadata.author()).isEqualTo("BBC News Mundo");

                // store quiz data
                this.quizOutboundData = quizOutboundDto;
        }

        @Test
        @Order(4)
        public void testCreateQuiz_whenQuizNameAlreadyExists_shouldThrowException() throws Exception {

                QuizCreateRequestDto quizCreateDto = new QuizCreateRequestDto(testUser.getId(),
                                testUser.getUsername(),
                                quizOutboundData.quizName(),
                                "https://www.youtube.com/watch?v=" + quizOutboundData.metadata()
                                                .videoUrl(),
                                Map.of("OPENAI_API_KEY", apiKeyTest),
                                quizOutboundData.language(),
                                quizOutboundData.type(),
                                quizOutboundData.difficulty());

                String quizCreateDtoJson = objectMapper.writeValueAsString(quizCreateDto);

                mockMvc.perform(MockMvcRequestBuilders.post("/users/{userId}/quizzes",
                                testUser.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(quizCreateDtoJson))
                                .andExpect(status().isCreated());

                // await the creation of quiz
                await()
                                .atMost(120, TimeUnit.SECONDS)
                                .pollInterval(2, TimeUnit.SECONDS)
                                .until(() -> isPresentAndTerminated.test(quizRequestService.getRequestByQuizRequestId(
                                                testUser.getId(), quizCreateRequestDto.quizName())));

                // check that no new user was created
                assertThat(userService.getAll().size()).isEqualTo(1);

                // check that QuizRequest has failed
                QuizRequest quizRequestRow = quizRequestService
                                .getRequestByQuizRequestId(testUser.getId(), quizCreateRequestDto.quizName())
                                .orElseThrow();

                assertThat(quizRequestRow.getStatus()).isEqualTo(QuizRequest.Status.FAILED);
                assertThat(quizRequestRow.getMessageInternal()).contains("already exists");
                assertThat(quizRequestRow.getMessageExternal()).contains("already exists");
        }

        @Test
        @Order(5)
        public void testCreateQuiz_whenInvalidAPIKeyProvided_shouldThrowException() throws Exception {

                QuizCreateRequestDto newQuizCreateDto = new QuizCreateRequestDto(testUser.getId(),
                                testUser.getUsername(),
                                "my second quiz",
                                "https://www.youtube.com/watch?v=" + quizOutboundData.metadata()
                                                .videoUrl(),
                                Map.of("OPENAI_API_KEY", "invalid api key"),
                                quizOutboundData.language(),
                                quizOutboundData.type(),
                                quizOutboundData.difficulty());

                String quizCreateDtoJson = objectMapper.writeValueAsString(newQuizCreateDto);

                mockMvc.perform(MockMvcRequestBuilders.post("/users/{userId}/quizzes",
                                testUser.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(quizCreateDtoJson))
                                .andExpect(status().isCreated());

                // await the creation of quiz
                await()
                                .atMost(120, TimeUnit.SECONDS)
                                .pollInterval(2, TimeUnit.SECONDS)
                                .until(() -> isPresentAndTerminated.test(quizRequestService.getRequestByQuizRequestId(
                                                testUser.getId(), newQuizCreateDto.quizName())));

                // check that no new user was created
                assertThat(userService.getAll().size()).isEqualTo(1);

                // check that QuizRequest has failed
                QuizRequest quizRequestRow = quizRequestService
                                .getRequestByQuizRequestId(testUser.getId(), newQuizCreateDto.quizName())
                                .orElseThrow();

                assertThat(quizRequestRow.getStatus()).isEqualTo(QuizRequest.Status.FAILED);
                assertThat(quizRequestRow.getMessageInternal()).contains("Incorrect API key");
                assertThat(quizRequestRow.getMessageExternal()).contains("Invalid OpenAI API key");
        }

        @Test
        @Order(6)
        public void testUpdateQuiz_whenValidQuizUpdateDataProvided_shouldUpdateQuizEntity() throws Exception {

                QuizUpdateDto quizUpdateDto = new QuizUpdateDto(testUser.getId(), quizOutboundData.quizId(),
                                quizOutboundData.numQuestions() - 1, quizOutboundData.quizName() + "_updated");
                String quizUpdateDtoJson = objectMapper.writeValueAsString(quizUpdateDto);

                String response = mockMvc.perform(MockMvcRequestBuilders.put("/users/{userId}/quizzes",
                                testUser.getId(), quizOutboundData.quizId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(quizUpdateDtoJson))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                QuizOutboundDto quizUpdateResultDto = objectMapper.readValue(response, QuizOutboundDto.class);

                assertThat(quizUpdateResultDto).isNotNull();
                assertThat(quizUpdateResultDto.userId()).isEqualTo(testUser.getId());
                assertThat(quizUpdateResultDto.quizId()).isEqualTo(quizOutboundData.quizId());
                assertThat(quizUpdateResultDto.numCorrect()).isEqualTo(quizUpdateDto.numCorrect());
                assertThat(quizUpdateResultDto.numTries()).isEqualTo(1);
                assertThat(quizUpdateResultDto.quizName()).isEqualTo(quizUpdateDto.quizName());
        }

        @Test
        @Order(7)
        public void testGetQuizDetails_whenValidUserQuizIdProvided_shouldFetchQuizDetails() throws Exception {

                String response = mockMvc
                                .perform(MockMvcRequestBuilders
                                                .get("/users/{userId}/quizzes/{quizId}/details", testUser.getId(),
                                                                quizOutboundData.quizId())
                                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                QuizDetailsOutboundDto quizDetailsDto = objectMapper.readValue(response, QuizDetailsOutboundDto.class);

                assertThat(quizDetailsDto).isNotNull();
                assertThat(quizDetailsDto.userId()).isEqualTo(testUser.getId());
                assertThat(quizDetailsDto.quizId()).isEqualTo(quizOutboundData.quizId());
                assertThat(quizDetailsDto.questionAnswersList()).hasSize(quizOutboundData.numQuestions());

                var quizQuestionFirst = quizDetailsDto.questionAnswersList()
                                .getFirst();
                assertThat(quizQuestionFirst.question()).hasSizeGreaterThan(0);
                assertThat(quizQuestionFirst.correctAnswer()).hasSizeGreaterThan(0);
                assertThat(quizQuestionFirst.wrongAnswers()).hasSize(3);
                assertThat(quizQuestionFirst.context()).hasSizeGreaterThan(0);
        }

        @Test
        @Order(8)
        public void testDeleteQuiz_whenValidUserQuizIdProvided_shouldDeleteQuiz() throws Exception {

                // delete quiz
                mockMvc.perform(MockMvcRequestBuilders
                                .delete("/users/{userId}/quizzes/{quizId}", testUser.getId(),
                                                quizOutboundData.quizId()))
                                .andExpect(status().isNoContent());

                // check that all rows in corresponding tables have been deleted
                assertThat(userQuizRepository.findById_UserIdAndId_QuizId(testUser.getId(), quizOutboundData.quizId())
                                .isPresent()).isFalse();

                assertThat(langchainPGCollectionRepository.findById(quizOutboundData.quizId())
                                .isPresent()).isFalse();

                assertThat(langchainPGEmbeddingRepository.findAll()
                                .stream()
                                .map(LangchainPGEmbedding::getCollectionId)
                                .filter(quizOutboundData.quizId()::equals)
                                .toList()).hasSize(0);
        }

        @Test
        @Order(9)
        public void testGetAllQuizRequests_whenValidUserIdProvided_shouldFetchQuizRequestHistory() throws Exception {

                String response = mockMvc
                                .perform(MockMvcRequestBuilders
                                                .get("/users/{userId}/quizzes/requests", testUser.getId())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .param("page", "0")
                                                .param("size", "10")
                                                .param("sort", "dateCreated,desc"))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                JsonNode rootNode = objectMapper.readTree(response);
                long totalElements = rootNode.path("page")
                                .path("totalElements")
                                .asLong();

                assertThat(totalElements).isEqualTo(2);
        }

        @Test
        @Order(10)
        public void testGetFinishedQuizRequests_whenValidUserIdAndStatusParamProvided_shouldFetchFinishedQuizRequests()
                        throws Exception {

                String response = mockMvc
                                .perform(MockMvcRequestBuilders
                                                .get("/users/{userId}/quizzes/requests", testUser.getId())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .param("page", "0")
                                                .param("size", "10")
                                                .param("sort", "dateCreated,desc")
                                                .param("status", "FINISHED"))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                JsonNode rootNode = objectMapper.readTree(response);
                long totalElements = rootNode.path("page")
                                .path("totalElements")
                                .asLong();

                // should be 0 'FINISHED' quiz requests, because first successful request was
                // overriden by
                // second attempt to create a quiz with same name
                assertThat(totalElements).isEqualTo(0);
        }

        @Test
        @Order(11)
        public void testGetFailedQuizRequests_whenValidUserIdAndStatusParamProvided_shouldFetchFailedQuizRequests()
                        throws Exception {

                String response = mockMvc
                                .perform(MockMvcRequestBuilders
                                                .get("/users/{userId}/quizzes/requests", testUser.getId())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .param("page", "0")
                                                .param("size", "10")
                                                .param("sort", "dateCreated,desc")
                                                .param("status", "FAILED"))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                JsonNode rootNode = objectMapper.readTree(response);
                long totalElements = rootNode.path("page")
                                .path("totalElements")
                                .asLong();

                assertThat(totalElements).isEqualTo(2);
        }

}
