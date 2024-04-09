package com.startingblock.domain.question.domain.repository;

import com.startingblock.domain.question.domain.QAType;
import com.startingblock.domain.question.domain.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long>, QuestionQuerydslRepository {

    List<Question> findByQuestionTypeAndIsAnswerd(QAType type, Boolean isAnswered);

    List<Question> findByAnnouncementIdAndQuestionTypeAndIsAnswerdAndIsNew(Long announcementId, QAType type, Boolean isAnswerd, Boolean isNew);

    List<Question> findByUserId(Long userId);
}
