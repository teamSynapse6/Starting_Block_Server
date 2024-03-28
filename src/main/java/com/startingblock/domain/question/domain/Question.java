package com.startingblock.domain.question.domain;

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
@Table(name = "question")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type")
    private QAType questionType;

    @Column(name = "is_answerd")
    private Boolean isAnswerd;

    // 보냈었는지, 처음 보내는지 체크 / 질문 객체를 만들 때는 true, 질문 전송시에 false로 변경
    @Column(name = "is_new")
    private Boolean isNew;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public void changeIsAnswerd() {
        this.isAnswerd = true;
    }
    public void changeIsNew() {
        this.isNew = false;
    }

    @Builder
    public Question(final String content, final QAType questionType, final User user) {
        this.content = content;
        this.questionType = questionType;
        this.isAnswerd = false;
        this.isNew = true;
        this.user = user;
    }
}
