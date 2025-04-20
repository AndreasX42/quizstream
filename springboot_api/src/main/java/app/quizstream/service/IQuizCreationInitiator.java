package app.quizstream.service;

import app.quizstream.dto.quiz.QuizCreateRequestDto;
import app.quizstream.entity.request.QuizRequest;

public interface IQuizCreationInitiator {

    void initiate(QuizCreateRequestDto quizCreateDto, QuizRequest quizJob);
}