package app.quizstream.service;

import app.quizstream.dto.user.UserRegisterDto;
import app.quizstream.entity.User;
import app.quizstream.exception.DuplicateEntityException;
import app.quizstream.repository.UserRepository;
import app.quizstream.security.config.EnvConfigs;
import app.quizstream.util.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

        @Mock
        private UserRepository userRepository;

        @Mock
        private EnvConfigs envConfigs;

        @Spy
        private UserMapper userMapper;

        @InjectMocks
        private UserService userService;

        private UUID existingUserId;
        private User existingUser;

        @BeforeEach
        public void setUp() {
                this.existingUserId = UUID.randomUUID();
                this.existingUser = new User();
                this.existingUser.setId(existingUserId);
                this.existingUser.setUsername("testUser");
                this.existingUser.setEmail("test@user.com");
        }

        @Test
        public void testCreateUser_whenExistingEmailProvided_shouldThrowDuplicateEntityException() {

                UserRegisterDto userDto = new UserRegisterDto(existingUserId, "testUser", "test@user.com");
                User user = userMapper.mapToEntity(userDto);

                when(userRepository.findByEmail(userDto.email())).thenReturn(Optional.of(user));

                assertThatThrownBy(() -> userService.create(userDto)).isInstanceOf(DuplicateEntityException.class)
                                .hasMessageContaining(
                                                "The user with email '" + userDto.email() + "' does already exist.");

        }

        @Test
        public void testCreateUser_whenExistingUsernameProvided_shouldThrowDuplicateEntityException() {

                UserRegisterDto userDto = new UserRegisterDto(existingUserId, "testUser", "test@user.com");
                User user = userMapper.mapToEntity(userDto);

                when(userRepository.findByUsername((userDto.username()))).thenReturn(Optional.of(user));

                assertThatThrownBy(() -> userService.create(userDto)).isInstanceOf(DuplicateEntityException.class)
                                .hasMessageContaining("The user with username '" + userDto.username()
                                                + "' does already exist.");
        }
}
