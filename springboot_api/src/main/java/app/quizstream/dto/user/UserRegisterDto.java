package app.quizstream.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Dto for registration of user.")
public record UserRegisterDto(

        @NotNull UUID id,
        @NotNull String username,
        @NotNull @Email String email) {

}
