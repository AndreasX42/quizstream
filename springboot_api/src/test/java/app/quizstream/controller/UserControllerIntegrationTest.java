package app.quizstream.controller;

import app.quizstream.dto.user.UserOutboundDto;
import app.quizstream.dto.user.UserRegisterDto;
import app.quizstream.entity.User;
import app.quizstream.entity.UserQuiz;
import app.quizstream.entity.UserQuizId;
import app.quizstream.entity.embedding.LangchainPGEmbedding;
import app.quizstream.entity.request.QuizRequestId;
import app.quizstream.repository.*;
import app.quizstream.security.config.EnvConfigs;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.escoffier.loom.loomunit.LoomUnitExtension;
import me.escoffier.loom.loomunit.ShouldNotPin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Commit;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Commit
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ExtendWith(LoomUnitExtension.class)
@ShouldNotPin
public class UserControllerIntegrationTest {

        private final UserRepository userRepository;
        private final UserQuizRepository userQuizRepository;
        private final QuizRequestRepository quizRequestRepository;
        private final LangchainPGCollectionRepository langchainPGCollectionRepository;
        private final LangchainPGEmbeddingRepository langchainPGEmbeddingRepository;
        private final EnvConfigs envConfigs;
        private final ObjectMapper objectMapper;
        private final MockMvc mockMvc;

        @Autowired
        public UserControllerIntegrationTest(UserRepository userRepository, UserQuizRepository userQuizRepository,
                        QuizRequestRepository quizRequestRepository,
                        LangchainPGCollectionRepository langchainPGCollectionRepository,
                        LangchainPGEmbeddingRepository langchainPGEmbeddingRepository, EnvConfigs envConfigs,
                        ObjectMapper objectMapper, MockMvc mockMvc) {
                this.userRepository = userRepository;
                this.userQuizRepository = userQuizRepository;
                this.quizRequestRepository = quizRequestRepository;
                this.langchainPGCollectionRepository = langchainPGCollectionRepository;
                this.langchainPGEmbeddingRepository = langchainPGEmbeddingRepository;
                this.envConfigs = envConfigs;
                this.objectMapper = objectMapper;
                this.mockMvc = mockMvc;
        }

        private User testUser;
        private User testAdmin;

        @BeforeAll
        public void setUp() {
                setUpUserAccount();
                setUpAdminAccount();
        }

        private void setUpUserAccount() {
                UUID userId = UUID.randomUUID();
                String userName = "John_Doe_1";

                testUser = new User();
                testUser.setId(userId);
                testUser.setUsername(userName);
                testUser.setEmail(userName + "@mail.com");
        }

        private void setUpAdminAccount() {
                UUID adminId = UUID.randomUUID();
                String adminName = "System_Admin";
                this.testAdmin = new User();
                this.testAdmin.setId(adminId);
                this.testAdmin.setUsername(adminName);
                this.testAdmin.setEmail(adminName + "@mail.com");
                userRepository.save(testAdmin);
        }

        @Test
        @Order(1)
        public void testRegisterUser_whenValidUserDetailsProvided_shouldCreateUserAndReturnUserInformation()
                        throws Exception {

                UserRegisterDto registerUserDto = new UserRegisterDto(testUser.getId(), testUser.getUsername(),
                                testUser.getEmail());
                String registerUserDtoJson = objectMapper.writeValueAsString(registerUserDto);

                String response = mockMvc.perform(MockMvcRequestBuilders.post(envConfigs.REGISTER_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerUserDtoJson))
                                .andExpect(status()
                                                .isCreated())
                                .andExpect(jsonPath("$.password")
                                                .doesNotExist())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                UserOutboundDto createdUserDto = objectMapper.readValue(response, UserOutboundDto.class);

                assertThat(createdUserDto).isNotNull();
                assertThat(createdUserDto.username()).isEqualTo(registerUserDto.username());
                assertThat(createdUserDto.email()).isEqualTo(registerUserDto.email());

                // store for next tests
                this.testUser.setId(createdUserDto.id());
        }

        @Test
        @Order(2)
        public void testGetUserById_whenValidUserIdProvided_shouldFetchCorrespondingUserFromDb() throws Exception {

                String response = mockMvc.perform(MockMvcRequestBuilders.get("/users/id/{id}", testUser.getId()))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                UserOutboundDto fetchedUserDto = objectMapper.readValue(response, UserOutboundDto.class);

                assertThat(fetchedUserDto.id()).isEqualTo(testUser.getId());
                assertThat(fetchedUserDto.username()).isEqualTo(testUser.getUsername());
                assertThat(fetchedUserDto.email()).isEqualTo(testUser.getEmail());
        }

        @Test
        @Order(3)
        public void testGetUserByUsername_whenValidUsernameProvided_shouldFetchCorrespondingUserFromDb()
                        throws Exception {

                String response = mockMvc
                                .perform(MockMvcRequestBuilders.get("/users/name/{name}", testUser.getUsername()))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                UserOutboundDto fetchedUserDto = objectMapper.readValue(response, UserOutboundDto.class);

                assertThat(fetchedUserDto.id()).isEqualTo(testUser.getId());
                assertThat(fetchedUserDto.username()).isEqualTo(testUser.getUsername());
                assertThat(fetchedUserDto.email()).isEqualTo(testUser.getEmail());
        }

        @Test
        @Order(4)
        public void testDeleteUser_whenValidUserIdProvided_shouldDeleteEntity() throws Exception {

                // get user info about quizzes created
                List<UUID> quizIds = userQuizRepository.findAll()
                                .stream()
                                .map(UserQuiz::getId)
                                .filter(id -> id
                                                .getUserId()
                                                .equals(testUser.getId()))
                                .map(UserQuizId::getQuizId)
                                .toList();

                // delete user
                mockMvc.perform(MockMvcRequestBuilders.delete("/users/{id}", testUser.getId()))
                                .andExpect(status().isNoContent());

                // check that all user rows from all tables have been deleted
                mockMvc.perform(MockMvcRequestBuilders.get("/users/id/{id}", testUser.getId()))
                                .andExpect(status().isNotFound());

                assertThat(userQuizRepository.findByUser_Id(testUser.getId(), Pageable.ofSize(1))
                                .getTotalElements()).isEqualTo(0);

                assertThat(quizRequestRepository.findAllById(quizIds.stream()
                                .map(quiz_id -> new QuizRequestId(testUser.getId(), quiz_id.toString()))
                                .toList())).hasSize(0);

                assertThat(langchainPGCollectionRepository.findAllById(quizIds)).hasSize(0);

                assertThat(langchainPGEmbeddingRepository.findAll()
                                .stream()
                                .map(LangchainPGEmbedding::getCollectionId)
                                .filter(quizIds::contains)).hasSize(0);

        }

}
