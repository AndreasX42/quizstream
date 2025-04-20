package app.quizstream.controller;

import app.quizstream.dto.quiz.*;
import app.quizstream.exception.ErrorResponse;
import app.quizstream.service.QuizService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/quizzes")
@Tag(name = "Quiz Controller")
public class LeaderboardController {

    private final QuizService quizService;

    public LeaderboardController(QuizService quizService) {
        this.quizService = quizService;
    }

    // GET leaderboard data
    @GetMapping(value = "/leaderboard", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Returns leaderboard data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "404", description = "Fetching data failed", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "200", description = "Successful retrieval data", content = @Content(schema = @Schema(implementation = QuizLeaderboardEntry.class))),
    })
    public ResponseEntity<Page<QuizLeaderboardEntry>> getLeaderboardData(Pageable pageable) {
        Page<QuizLeaderboardEntry> data = quizService.getLeaderboardData(pageable);
        return new ResponseEntity<>(data, HttpStatus.OK);
    }
}
