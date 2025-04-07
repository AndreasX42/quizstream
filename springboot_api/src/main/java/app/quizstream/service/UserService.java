package app.quizstream.service;

import app.quizstream.dto.user.UserOutboundDto;
import app.quizstream.dto.user.UserRegisterDto;
import app.quizstream.entity.User;
import app.quizstream.exception.DuplicateEntityException;
import app.quizstream.exception.EntityNotFoundException;
import app.quizstream.repository.UserRepository;
import app.quizstream.util.mapper.UserMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    public User getByUserName(String username) {
        Optional<User> userOptional = userRepository.findByUsername(username);
        return userOptional.orElseThrow(() -> new EntityNotFoundException(username, User.class));
    }

    public User getById(UUID id) {
        Optional<User> userOptional = userRepository.findById(id);
        return userOptional.orElseThrow(() -> new EntityNotFoundException(id.toString(), User.class));
    }

    public Page<UserOutboundDto> getAll(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(userMapper::mapFromEntityOutbound);
    }

    public List<User> getAll() {
        return userRepository.findAll();
    }

    public UserOutboundDto create(UserRegisterDto userDto) {
        if (userRepository.findByEmail(userDto.email())
                .isPresent()) {
            throw new DuplicateEntityException("email", userDto.email(), User.class);
        }

        if (userRepository.findByUsername(userDto.username())
                .isPresent()) {
            throw new DuplicateEntityException("username", userDto.username(), User.class);
        }

        User user = userMapper.mapToEntity(userDto);
        return userMapper.mapFromEntityOutbound(userRepository.save(user));
    }

    public void delete(UUID id) {
        if (userRepository.findById(id)
                .isEmpty()) {
            throw new EntityNotFoundException(id.toString(), User.class);
        }

        userRepository.deleteById(id);
    }

}
