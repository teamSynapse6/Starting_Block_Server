package com.startingblock.domain.question.domain.repository;

import com.startingblock.domain.question.domain.Question;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long>, QuestionQuerydslRepository {
}
