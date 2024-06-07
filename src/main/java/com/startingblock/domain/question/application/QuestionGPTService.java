package com.startingblock.domain.question.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.startingblock.domain.answer.application.AnswerService;
import com.startingblock.domain.answer.domain.repository.AnswerRepository;
import com.startingblock.domain.answer.dto.AnswerRequestDto;
import com.startingblock.domain.gpt.application.GptService;
import com.startingblock.domain.gpt.dto.DuplicateReq;
import com.startingblock.domain.gpt.dto.SimpleDuplicateReq;
import com.startingblock.domain.question.domain.QAType;
import com.startingblock.domain.question.domain.Question;
import com.startingblock.domain.question.domain.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionGPTService {

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final GptService gptService;
    private final AnswerService answerService;

    // GPT 활용, 기존 질문과 유사 비교
    @Async
    @Transactional
    public void checkDuplicateQuestion(final Question checkQuestion) throws JsonProcessingException {
        List<Question> questionList = questionRepository.findByAnnouncementIdAndQuestionTypeAndIsAnswerd(
                checkQuestion.getAnnouncement().getId(), QAType.CONTACT, true);
        List<SimpleDuplicateReq> oldQuestions = questionList.stream()
                .map(question -> SimpleDuplicateReq.builder()
                        .qid(question.getId())
                        .content(question.getContent())
                        .build())
                .collect(Collectors.toList());

        DuplicateReq duplicateReq= DuplicateReq.builder()
                .oldQuestions(oldQuestions)
                .newQuestion(checkQuestion.getContent())
                .build();

        Long response = gptService.checkDuplicateQuestion(duplicateReq);
        if (!response.equals(0L)) { // 기존 질문과 비슷한 경우
            log.info("기존 질문" + response + " 번과 비슷합니다.");
            AnswerRequestDto.AnswerRequest dto = AnswerRequestDto.AnswerRequest.builder()
                    .questionId(checkQuestion.getId())
                    .content(answerRepository.findByQuestionIdAndAnswerType(response, QAType.CONTACT).getContent())
                    .build();

            answerService.sendContactAnswer(dto);
        }
    }
}
