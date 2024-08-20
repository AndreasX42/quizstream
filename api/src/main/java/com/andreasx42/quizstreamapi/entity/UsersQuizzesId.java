package com.andreasx42.quizstreamapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
public class UsersQuizzesId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "quiz_name")
    private String quizName;

}