package com.startingblock.domain.question.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.domain.announcement.exception.InvalidAnnouncementException;
import com.startingblock.domain.question.domain.repository.QuestionRepository;
import com.startingblock.domain.question.dto.QuestionResponseDto;
import com.startingblock.domain.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionFindService {

    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final AnnouncementRepository announcementRepository;

    // TODO: 질문 리스트 조회
    public List<QuestionResponseDto.QuestionListResponse> findByAnnouncement(final Long announcementId) {
        announcementRepository.findById(announcementId)
                .orElseThrow(InvalidAnnouncementException::new);

        return questionRepository.findQuestionListByAnnouncementId(announcementId);
    }

//    // TODO: 질문 상세 조회
//    public List<> findDetail(final Long questionId) {
//
//    }
}
