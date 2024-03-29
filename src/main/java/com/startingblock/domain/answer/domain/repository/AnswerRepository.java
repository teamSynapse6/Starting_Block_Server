package com.startingblock.domain.answer.domain.repository;

import com.startingblock.domain.answer.domain.Answer;
import com.startingblock.domain.question.domain.QAType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerRepository extends JpaRepository<Answer, Long>, AnswerQuerydslRepository {

    Integer countByQuestionIdAndAnswerType(Long questionId, QAType answerType);
}
