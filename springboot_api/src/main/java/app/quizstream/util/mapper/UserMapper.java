package app.quizstream.util.mapper;

import org.springframework.stereotype.Component;

import app.quizstream.dto.user.UserOutboundDto;
import app.quizstream.dto.user.UserRegisterDto;
import app.quizstream.entity.User;

@Component
public class UserMapper {

    public UserOutboundDto mapFromEntityOutbound(User user) {
        return new UserOutboundDto(user.getId(), user.getUsername(), user.getEmail());
    }

    public User mapToEntity(UserRegisterDto userDto) {
        User user = new User();
        user.setId(userDto.id());
        user.setUsername(userDto.username());
        user.setEmail(userDto.email());
        return user;
    }

}
