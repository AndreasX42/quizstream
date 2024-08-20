package com.andreasx42.quizstreamapi.repository;


import com.andreasx42.quizstreamapi.entity.UsersQuizzes;
import com.andreasx42.quizstreamapi.entity.UsersQuizzesId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsersQuizzesRepository extends JpaRepository<UsersQuizzes, UsersQuizzesId> {

    double x = 0;

    Optional<UsersQuizzes> findByUserIdAndQuizName(Long studentId, String quizName);


}