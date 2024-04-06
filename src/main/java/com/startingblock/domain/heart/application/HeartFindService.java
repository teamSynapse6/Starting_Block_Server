package com.startingblock.domain.heart.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.AnnouncementType;
import com.startingblock.domain.heart.domain.Heart;
import com.startingblock.domain.heart.domain.HeartType;
import com.startingblock.domain.heart.domain.repository.HeartRepository;
import com.startingblock.domain.heart.dto.HeartResponseDto;
import com.startingblock.domain.question.domain.Question;
import com.startingblock.domain.question.domain.repository.QuestionRepository;
import com.startingblock.domain.user.domain.User;
import com.startingblock.domain.user.domain.repository.UserRepository;
import com.startingblock.domain.user.exception.InvalidUserException;
import com.startingblock.global.config.security.token.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HeartFindService {

    private final HeartRepository heartRepository;
    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;

    // TODO: 마이페이지 - 내 궁금해요 조회
    public List<HeartResponseDto.MyHeartResponse> findMyHeart(UserPrincipal userPrincipal) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);

        List<Heart> hearts = heartRepository.findAllByUserIdAndHeartType(user.getId(), HeartType.QUESTION);
        List<HeartResponseDto.MyHeartResponse> response = new ArrayList<>();
        for (Heart heart : hearts) {
            Announcement announcement = heart.getQuestion().getAnnouncement();
            Question question = heart.getQuestion();
            Long count = questionRepository.findAnswerAndReplyCountByQuestionId(question.getId());
            response.add(HeartResponseDto.MyHeartResponse.builder()
                    .announcementType(announcement.getAnnouncementType() == AnnouncementType.ON_CAMPUS ? "교내" : "교외")
                    .announcementName(announcement.getTitle())
                    .questionId(question.getId())
                    .questionContent(question.getContent())
                    .answerCount(count.intValue())
                    .build());
        }
        return response;
    }
}
