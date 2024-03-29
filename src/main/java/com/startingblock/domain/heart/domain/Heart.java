package com.startingblock.domain.heart.domain;

import com.startingblock.domain.answer.domain.Answer;
import com.startingblock.domain.common.BaseEntity;
import com.startingblock.domain.question.domain.Question;
import com.startingblock.domain.reply.domain.Reply;
import com.startingblock.domain.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "heart")
public class Heart extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "heart_type")
    private HeartType heartType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id")
    private Answer answer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_id")
    private Reply reply;

    public void updateQuestion(final Question question) {
        this.question = question;
    }

    public void updateAnswer(final Answer answer) {
        this.answer = answer;
    }

    public void updateReply(final Reply reply) {
        this.reply = reply;
    }

    @Builder
    public Heart(final HeartType heartType, final User user) {
        this.heartType = heartType;
        this.user = user;
    }
}
