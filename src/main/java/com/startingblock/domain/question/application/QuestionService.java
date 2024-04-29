package com.startingblock.domain.question.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.domain.announcement.exception.InvalidAnnouncementException;
import com.startingblock.domain.mail.application.MailService;
import com.startingblock.domain.mail.dto.MailRequestDto;
import com.startingblock.domain.question.domain.QAType;
import com.startingblock.domain.question.domain.Question;
import com.startingblock.domain.question.domain.repository.QuestionRepository;
import com.startingblock.domain.question.dto.QuestionRequestDto;
import com.startingblock.domain.user.domain.User;
import com.startingblock.domain.user.domain.repository.UserRepository;
import com.startingblock.domain.user.exception.InvalidUserException;
import com.startingblock.global.config.security.token.UserPrincipal;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final AnnouncementRepository announcementRepository;
    private final UserRepository userRepository;
    private final MailService mailService;

    // TODO: 질문하기
    @Transactional
    public void ask(final UserPrincipal userPrincipal, final QuestionRequestDto.AskQuestionRequest dto) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);

        Announcement announcement = announcementRepository.findById(dto.getAnnouncementId())
                .orElseThrow(InvalidAnnouncementException::new);

        Question question = Question.builder()
                .content(dto.getContent())
                .questionType(dto.getIsContact() ? QAType.CONTACT : QAType.GENERAL)
                .user(user)
                .announcement(announcement)
                .build();

        questionRepository.save(question);
    }

//    @Scheduled(cron = "0 * * * * *") // 매분 0초마다 실행 (테스트)
//    @Scheduled(cron = "0 0 9 * * *") // 매일 오전 9시에 실행
    public void sendContactEmail() throws MessagingException, UnsupportedEncodingException {

        LocalDate today = LocalDate.now();
        DayOfWeek dayOfWeek = today.getDayOfWeek();

        // 주말인 경우 작업을 실행하지 않음
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return;
        }

        List<Question> questionList = questionRepository.findByQuestionTypeAndIsAnswerd(QAType.CONTACT, false);
        if (questionList.isEmpty()) {
            log.info("문의처 답변이 달려야할 질문이 없습니다.");
            return;
        }

        for (Question question : questionList) {
            Announcement announcement = question.getAnnouncement();
            // 담당자 이메일이 없으면 continue
            if (announcement.getContact() == null) {
                log.info(announcement.getTitle() + "의 이메일이 없습니다.");
                continue;
            }
            MailRequestDto mail = MailRequestDto.builder()
                    .email(announcement.getContact())
                    .announcement(announcement.getTitle())
                    .link("https://www.startingblock.co.kr/questions/" + announcement.getId())
                    .build();
            mailService.sendEmailToReceiver(mail);
        }
    }
}
