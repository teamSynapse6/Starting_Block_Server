package com.startingblock.domain.question.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.domain.announcement.exception.InvalidAnnouncementException;
import com.startingblock.domain.question.domain.QAType;
import com.startingblock.domain.question.domain.Question;
import com.startingblock.domain.question.domain.repository.QuestionRepository;
import com.startingblock.domain.question.dto.QuestionRequestDto;
import com.startingblock.domain.user.domain.User;
import com.startingblock.domain.user.domain.repository.UserRepository;
import com.startingblock.domain.user.exception.InvalidUserException;
import com.startingblock.global.config.security.token.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final AnnouncementRepository announcementRepository;
    private final UserRepository userRepository;

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
}
