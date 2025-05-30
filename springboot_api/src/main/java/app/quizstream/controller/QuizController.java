package app.quizstream.controller;

import app.quizstream.dto.quiz.*;
import app.quizstream.entity.request.QuizRequest;
import app.quizstream.exception.ErrorResponse;
import app.quizstream.service.QuizRequestService;
import app.quizstream.service.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.UUID;

@RestController
@RequestMapping("/users/{userId}/quizzes")
@Tag(name = "Quiz Controller", description = "Endpoints to create and manage quizzes")
public class QuizController {

    private final QuizService quizService;
    private final QuizRequestService quizRequestsService;

    public QuizController(QuizService quizService, QuizRequestService quizJobsService) {
        this.quizService = quizService;
        this.quizRequestsService = quizJobsService;
    }

    // GET quiz by quiz id
    @GetMapping(value = "/{quizId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("#userId.toString() == principal.claims['sub'] or hasAuthority('ADMIN')")
    @Operation(summary = "Returns a quiz based on provided user and quiz id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "404", description = "Quiz doesn't exist", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "200", description = "Successful retrieval of quiz", content = @Content(schema = @Schema(implementation = QuizOutboundDto.class))),
    })
    public ResponseEntity<QuizOutboundDto> getQuizByUserQuizId(@PathVariable UUID userId, @PathVariable UUID quizId) {

        QuizOutboundDto quiz = quizService.getQuizByUserQuizId(userId, quizId);

        return new ResponseEntity<>(quiz, HttpStatus.OK);
    }

    // GET all quizzes by user id
    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("#userId.toString() == principal.claims['sub'] or hasAuthority('ADMIN')")
    @Operation(summary = "Retrieves paged list users quizzes")
    @ApiResponse(responseCode = "200", description = "Successful retrieval of all quizzes of user", content = @Content(array = @ArraySchema(schema = @Schema(implementation = QuizOutboundDto.class))))
    public ResponseEntity<Page<QuizOutboundDto>> getAllUserQuizzes(@PathVariable UUID userId, Pageable pageable) {

        Page<QuizOutboundDto> userQuizzes = quizService.getAllUserQuizzes(userId, pageable);

        return new ResponseEntity<>(userQuizzes, HttpStatus.OK);
    }

    // CREATE quiz by userid
    @PostMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("#userId.toString() == principal.claims['sub'] or hasAuthority('ADMIN')")
    @Operation(summary = "Creates a quiz from provided data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Successfully started quiz creation", content = @Content(schema = @Schema(implementation = QuizRequestDto.class))),
            @ApiResponse(responseCode = "400", description = "Bad request: unsuccessful submission", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    })
    public ResponseEntity<QuizRequestDto> createQuiz(@PathVariable UUID userId,
            @Valid @RequestBody QuizCreateRequestDto quizCreateDto) {

        QuizRequestDto quizJobDto = quizService.createQuiz(quizCreateDto);

        return new ResponseEntity<>(quizJobDto, HttpStatus.CREATED);
    }

    // UPDATE quiz
    @PutMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("#userId.toString() == principal.claims['sub'] or hasAuthority('ADMIN')")
    @Operation(summary = "Updates a quiz by user id, quiz id and provided payload")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful update of quiz", content = @Content(schema = @Schema(implementation = QuizOutboundDto.class))),
            @ApiResponse(responseCode = "400", description = "Bad request: unsuccessful submission", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<QuizOutboundDto> updateQuiz(@PathVariable UUID userId,
            @Valid @RequestBody QuizUpdateDto quizUpdateDto) {

        QuizOutboundDto updatedQuiz = quizService.updateQuiz(quizUpdateDto);

        return new ResponseEntity<>(updatedQuiz, HttpStatus.OK);
    }

    // DELETE quiz
    @DeleteMapping("/{quizId}")
    @PreAuthorize("#userId.toString() == principal.claims['sub'] or hasAuthority('ADMIN')")
    @Operation(summary = "Deletes quiz with given user and quiz id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Successful deletion of quiz", content = @Content(schema = @Schema(implementation = HttpStatus.class))),
            @ApiResponse(responseCode = "400", description = "Bad request: unsuccessful submission", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<HttpStatus> deleteQuiz(@PathVariable UUID userId, @PathVariable UUID quizId) {

        quizService.deleteQuiz(userId, quizId);

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    // GET quiz details by user and quiz id
    @GetMapping(value = "/{quizId}/details", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("#userId.toString() == principal.claims['sub'] or hasAuthority('ADMIN')")
    @Operation(summary = "Returns the quiz details based on provided user and quiz id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "404", description = "Quiz details don't exist", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "200", description = "Successful retrieval of quiz details", content = @Content(schema = @Schema(implementation = QuizDetailsOutboundDto.class))),
    })
    public ResponseEntity<QuizDetailsOutboundDto> getQuizDetailsByUserQuizId(@PathVariable UUID userId,
            @PathVariable UUID quizId) {

        QuizDetailsOutboundDto quizDetails = quizService.getQuizDetailsByUserQuizId(userId, quizId);

        return new ResponseEntity<>(quizDetails, HttpStatus.OK);
    }

    // GET returns list of quiz jobs of user
    @GetMapping(value = "/requests", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("#userId.toString() == principal.claims['sub'] or hasAuthority('ADMIN')")
    @Operation(summary = "Returns paged list of quiz requests for given user id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "404", description = "Fetching quiz jobs failed", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "200", description = "Successful retrieval of list of quiz jobs", content = @Content(schema = @Schema(implementation = QuizRequestDto.class))),
    })
    public ResponseEntity<Page<QuizRequestDto>> getQuizRequestsByUserId(@PathVariable UUID userId,
            @RequestParam(required = false) QuizRequest.Status status, Pageable pageable) {

        Page<QuizRequestDto> quizJobs = quizRequestsService.getRequestsForUserId(userId, status, pageable);

        return new ResponseEntity<>(quizJobs, HttpStatus.OK);
    }

    // DELETE quiz job
    @DeleteMapping("/requests")
    @PreAuthorize("#quizDeleteRequestDto.userId.toString() == principal.claims['sub'] or hasAuthority('ADMIN')")
    @Operation(summary = "Deletes quiz request with given user id and quiz name")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Successful deletion of quiz job", content = @Content(schema = @Schema(implementation = HttpStatus.class))),
            @ApiResponse(responseCode = "400", description = "Bad request: unsuccessful submission", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<HttpStatus> deleteQuizRequestForUser(
            @Valid @RequestBody QuizDeleteRequestDto quizDeleteRequestDto) {

        quizRequestsService.deleteRequestForUser(quizDeleteRequestDto);

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

}
