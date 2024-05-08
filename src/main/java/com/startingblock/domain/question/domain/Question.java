package com.startingblock.domain.question.domain;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.common.BaseEntity;
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
public class Question extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type")
    private QAType questionType;

    // 문의처 답변이 달리면 changeIsAnswerd() 호출, 문의처 질문에 활용하기 위함.
    @Column(name = "is_answerd")
    private Boolean isAnswerd;

    // 답변완료 or 다음에 답하기 누르면 false로 변경 / isAnswerd==false && isNew==false 이면 재전송 질문
    @Column(name = "is_new")
    private Boolean isNew;

    @Column(name = "is_Send")
    private Boolean isSend;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "announcement_id", nullable = false)
    private Announcement announcement;

    public void changeIsAnswerd() {
        this.isAnswerd = true;
    }

    public void changeIsNew() {
        this.isNew = false;
    }

    @Builder
    public Question(final String content, final QAType questionType, final User user, final Announcement announcement) {
        this.content = content;
        this.questionType = questionType;
        this.isAnswerd = false;
        this.isNew = true;
        this.isSend = false;
        this.user = user;
        this.announcement = announcement;
    }
}
