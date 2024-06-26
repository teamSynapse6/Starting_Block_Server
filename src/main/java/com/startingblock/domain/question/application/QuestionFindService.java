package com.startingblock.domain.question.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startingblock.domain.announcement.application.CampusFindService;
import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.AnnouncementType;
import com.startingblock.domain.announcement.domain.University;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.domain.announcement.exception.InvalidAnnouncementException;
import com.startingblock.domain.answer.domain.Answer;
import com.startingblock.domain.answer.domain.repository.AnswerRepository;
import com.startingblock.domain.answer.dto.AnswerResponseDto;
import com.startingblock.domain.gpt.application.GptService;
import com.startingblock.domain.gpt.dto.GroupingQuestionReq;
import com.startingblock.domain.gpt.dto.GroupingQuestionRes;
import com.startingblock.domain.heart.domain.Heart;
import com.startingblock.domain.heart.domain.repository.HeartRepository;
import com.startingblock.domain.question.domain.QAType;
import com.startingblock.domain.question.domain.Question;
import com.startingblock.domain.question.domain.repository.QuestionRepository;
import com.startingblock.domain.question.dto.QuestionResponseDto;
import com.startingblock.domain.question.exception.InvalidQuestionException;
import com.startingblock.domain.reply.domain.repository.ReplyRepository;
import com.startingblock.domain.user.domain.User;
import com.startingblock.domain.user.domain.repository.UserRepository;
import com.startingblock.domain.user.exception.InvalidUserException;
import com.startingblock.global.config.security.token.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class QuestionFindService {

    private final QuestionRepository questionRepository;
    private final AnnouncementRepository announcementRepository;
    private final AnswerRepository answerRepository;
    private final HeartRepository heartRepository;
    private final UserRepository userRepository;
    private final ReplyRepository replyRepository;
    private final GptService gptService;
    private final ObjectMapper objectMapper;

    // TODO: 질문 리스트 조회
    public List<QuestionResponseDto.QuestionListResponse> findByAnnouncement(final UserPrincipal userPrincipal, final Long announcementId) {
        announcementRepository.findById(announcementId)
                .orElseThrow(InvalidAnnouncementException::new);
        return questionRepository.findQuestionListByAnnouncementId(userPrincipal.getId(), announcementId);
    }

    // TODO: 나의 질문 조회
    public List<QuestionResponseDto.MyQuestionListResponse> findMyQuestion(final UserPrincipal userPrincipal) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);
        List<Question> questions = questionRepository.findByUserId(user.getId());
        List<QuestionResponseDto.MyQuestionListResponse> response = new ArrayList<>();
        for (Question question : questions) {
            Announcement announcement = question.getAnnouncement();
            int answerAndReplyCount = countAllAnswerAndReply(question.getId());
            AnswerResponseDto.ContactAnswerResponse contactAnswer = answerRepository.findContactAnswer(question.getId());

            response.add(QuestionResponseDto.MyQuestionListResponse.builder()
                    .announcementId(announcement.getId())
                    .announcementType(announcement.getAnnouncementType() == AnnouncementType.ON_CAMPUS ? "교내" : "교외")
                    .announcementName(announcement.getTitle())
                    .questionId(question.getId())
                    .questionContent(question.getContent())
                    .createdAt(question.getCreatedAt())
                    .heartCount(heartRepository.countByQuestionId(question.getId()))
                    .answerCount(answerAndReplyCount)
                    .organizationManger(contactAnswer != null ? contactAnswer.getOrganizationManger() : null)
                    .contactAnswerContent(contactAnswer != null ? contactAnswer.getContent() : null)
                    .build());
        }
        return response;
    }

    // TODO: 질문 상세 조회
    public QuestionResponseDto.QuestionDetailResponse findDetail(final UserPrincipal userPrincipal, final Long questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(InvalidQuestionException::new);
        Optional<Heart> heart = heartRepository.findByUserIdAndQuestionId(userPrincipal.getId(), questionId);

        return QuestionResponseDto.QuestionDetailResponse.builder()
                .userName(question.getUser().getNickname())
                .profileNumber(question.getUser().getProfileNumber())
                .content(question.getContent())
                .createdAt(question.getCreatedAt())
                .heartCount(heartRepository.countByQuestionId(questionId))
                .isMyHeart(heartRepository.existsByQuestionIdAndUserId(questionId, userPrincipal.getId()))
                .heartId(heart.isPresent() ? heart.get().getId() : null)
                .contactAnswer(answerRepository.findContactAnswer(questionId))
                .answerCount(answerRepository.countByQuestionIdAndAnswerType(questionId, QAType.GENERAL))
                .answerList(answerRepository.findAnswerList(questionId, userPrincipal.getId()))
                .build();
    }

    // TODO: 웹 - 공고별 질문 리스트 조회
    public QuestionResponseDto.QuestionListResponseForWeb findByAnnouncementForWeb(final Long announcementId) throws JsonProcessingException {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(InvalidAnnouncementException::new);

        List<Question> oldQuestionList = questionRepository.findByAnnouncementIdAndQuestionTypeAndIsAnswerdAndIsNew(announcementId, QAType.CONTACT, false, false);
        List<Question> newQuestionList = questionRepository.findByAnnouncementIdAndQuestionTypeAndIsAnswerdAndIsNew(announcementId, QAType.CONTACT, false, true);
        List<QuestionResponseDto.QuestionSimpleResponse> oldQuestions = new ArrayList<>();
        List<QuestionResponseDto.QuestionSimpleResponse> newQuestions = new ArrayList<>();

        for (Question question : oldQuestionList) {
            oldQuestions.add(QuestionResponseDto.QuestionSimpleResponse.builder()
                    .questionId(question.getId())
                    .content(question.getContent())
                    .build());
        }
        for (Question question : newQuestionList) {
            newQuestions.add(QuestionResponseDto.QuestionSimpleResponse.builder()
                    .questionId(question.getId())
                    .content(question.getContent())
                    .build());
        }
        // gpt로 유사 질문 그룹핑
        String strOldQuestions = gptService.groupingQuestions(GroupingQuestionReq.builder().questions(oldQuestions).build());
        List<GroupingQuestionRes> groupingOldQuestions = objectMapper.readValue(strOldQuestions, new TypeReference<List<GroupingQuestionRes>>() {});
        String strNewQuestions = gptService.groupingQuestions(GroupingQuestionReq.builder().questions(newQuestions).build());
        List<GroupingQuestionRes> groupingNewQuestions = objectMapper.readValue(strNewQuestions, new TypeReference<List<GroupingQuestionRes>>() {});

        return QuestionResponseDto.QuestionListResponseForWeb.builder()
                .announcementName(announcement.getTitle())
                .detailUrl(announcement.getDetailUrl())
                .oldQuestions(groupingOldQuestions)
                .newQuestions(groupingNewQuestions)
                .build();
    }

    // TODO: 해당 질문에 달린 댓글 + 대댓글 수 구하기
    public int countAllAnswerAndReply(Long questionId) {
        Integer answerCount = answerRepository.countByQuestionIdAndAnswerType(questionId, QAType.GENERAL);
        List<Answer> answers = answerRepository.findByQuestionId(questionId);
        int replyCount = 0;
        for (Answer answer : answers) {
            replyCount += replyRepository.countByAnswerId(answer.getId());
        }
        return answerCount + replyCount;
    }

    // TODO: 질문 발송 상태 조회
    public List<QuestionResponseDto.QuestionResponseForStatusCheck> findQuestionForStatusCheck(final UserPrincipal userPrincipal) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);

        List<QuestionResponseDto.QuestionResponseForStatusCheck> responseList = new ArrayList<>();
        List<Question> contactQuestions = questionRepository.findQuestionsForStatusCheck(user.getId());

        for (Question contactQuestion : contactQuestions) {
            LocalDateTime receptionTime = contactQuestion.getCreatedAt();
            LocalDateTime sendTime = contactQuestion.getIsSend() ? receptionTime.plusDays(1) : null;
            LocalDateTime arriveTime = contactQuestion.getIsAnswerd() ? contactQuestion.getUpdatedAt() : null;

            responseList.add(QuestionResponseDto.QuestionResponseForStatusCheck.builder()
                    .title(contactQuestion.getAnnouncement().getTitle())
                    .questionId(contactQuestion.getId())
                    .content(contactQuestion.getContent())
                    .receptionTime(receptionTime)
                    .sendTime(sendTime)
                    .arriveTime(arriveTime)
                    .build());
        }
        return responseList;
    }

    public List<QuestionResponseDto.WaitingAnswer> findQuestionWaitingAnswer(final UserPrincipal userPrincipal) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);
        List<QuestionResponseDto.WaitingAnswer> responseList = new ArrayList<>();
        University university = CampusFindService.findUserUniversity(user);

        // 교외1 + 교내1 -> user - university가 있고, 10개 대학 중 하나인 경우
        if (university != null) { //
            List<Question> questions = questionRepository.findQuestionWaitingAnswerOnOff(user.getId(), university);
            for (Question question : questions) {
                Announcement announcement = question.getAnnouncement();
                responseList.add(QuestionResponseDto.WaitingAnswer.builder()
                        .announcementId(announcement.getId())
                        .announcementType(announcement.getAnnouncementType() == AnnouncementType.ON_CAMPUS ? "교내" : "교외")
                        .announcementTitle(announcement.getTitle())
                        .questionId(question.getId())
                        .questionContent(question.getContent())
                        .heartCount(heartRepository.countByQuestionId(question.getId()))
                        .createdAt(question.getCreatedAt())
                        .build());
            }
            return responseList;
        }

        // 교외 2 -> user - university가 없고, 있어도 10개 대학이 아니면
        List<Question> questions = questionRepository.findQuestionWaitingAnswerOff(user.getId());
        for (Question question : questions) {
            log.info(String.valueOf(questions.size()));
            Announcement announcement = question.getAnnouncement();
            responseList.add(QuestionResponseDto.WaitingAnswer.builder()
                    .announcementId(announcement.getId())
                    .announcementType("교외")
                    .announcementTitle(announcement.getTitle())
                    .questionId(question.getId())
                    .questionContent(question.getContent())
                    .heartCount(heartRepository.countByQuestionId(question.getId()))
                    .createdAt(question.getCreatedAt())
                    .build());
        }
        return responseList;
    }
}
