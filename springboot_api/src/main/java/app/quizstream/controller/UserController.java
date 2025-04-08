package app.quizstream.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import app.quizstream.dto.user.UserOutboundDto;
import app.quizstream.dto.user.UserRegisterDto;
import app.quizstream.exception.ErrorResponse;
import app.quizstream.service.UserService;
import app.quizstream.util.mapper.UserMapper;

@RestController
@RequestMapping("/users")
@AllArgsConstructor
@Tag(name = "User Controller", description = "Endpoints to create and manage users")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    @Operation(summary = "Returns a user based on id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "200", description = "User found", content = @Content(schema = @Schema(implementation = UserOutboundDto.class))), })
    @GetMapping(value = "id/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("#id == principal.claims['cognito:username'] or hasAuthority('ADMIN')")
    public ResponseEntity<UserOutboundDto> getUserById(@PathVariable UUID id) {

        UserOutboundDto userDto = userMapper.mapFromEntityOutbound(userService.getById(id));
        return new ResponseEntity<>(userDto, HttpStatus.OK);
    }

    @Operation(summary = "Returns a user based on username")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "200", description = "User found", content = @Content(schema = @Schema(implementation = UserOutboundDto.class))), })
    @GetMapping(value = "name/{userName}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("#userName == principal.claims['cognito:username'] or hasAuthority('ADMIN')")
    public ResponseEntity<UserOutboundDto> getUserByUserName(@PathVariable String userName) {

        UserOutboundDto userDto = userMapper.mapFromEntityOutbound(userService.getByUserName(userName));
        return new ResponseEntity<>(userDto, HttpStatus.OK);
    }

    @Operation(summary = "Creates a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful creation of user", content = @Content(schema = @Schema(implementation = UserOutboundDto.class))),
            @ApiResponse(responseCode = "400", description = "Creation of user not successful", content = @Content(schema = @Schema(implementation = ErrorResponse.class))) })
    @PostMapping(value = "/register", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserOutboundDto> registerUser(@Valid @RequestBody UserRegisterDto userDto) {

        UserOutboundDto userRegisteredDto = userService.create(userDto);
        return new ResponseEntity<>(userRegisteredDto, HttpStatus.CREATED);
    }

    @Operation(summary = "Deletes user with given id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Successful deletion of user", content = @Content(schema = @Schema(implementation = HttpStatus.class))),
            @ApiResponse(responseCode = "400", description = "Bad request: unsuccessful submission", content = @Content(schema = @Schema(implementation = ErrorResponse.class))) })
    @DeleteMapping(value = "/{id}")
    @PreAuthorize("#id == principal.claims['cognito:username'] or hasAuthority('ADMIN')")
    public ResponseEntity<HttpStatus> deleteUser(@PathVariable UUID id) {

        userService.delete(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
