package com.startingblock.domain.answer.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.AnnouncementType;
import com.startingblock.domain.answer.domain.Answer;
import com.startingblock.domain.answer.domain.repository.AnswerRepository;
import com.startingblock.domain.answer.dto.AnswerResponseDto;
import com.startingblock.domain.heart.domain.repository.HeartRepository;
import com.startingblock.domain.question.domain.repository.QuestionRepository;
import com.startingblock.domain.question.dto.QuestionResponseDto;
import com.startingblock.domain.reply.domain.Reply;
import com.startingblock.domain.reply.domain.repository.ReplyRepository;
import com.startingblock.domain.user.domain.User;
import com.startingblock.domain.user.domain.repository.UserRepository;
import com.startingblock.domain.user.exception.InvalidUserException;
import com.startingblock.global.DefaultAssert;
import com.startingblock.global.config.security.token.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnswerFindService {

    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final ReplyRepository replyRepository;
    private final HeartRepository heartRepository;

    // TODO: 내 답변 + 댓글 조회
    public List<AnswerResponseDto.MyWriteResponse> findMyAnswerAndReply(UserPrincipal userPrincipal) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);
        List<QuestionResponseDto.QuestionByMyAnswerAndReply> results = questionRepository.findQuestionByMyAnswerAndReply(user.getId());
        List<AnswerResponseDto.MyWriteResponse> responses = new ArrayList<>();

        for (QuestionResponseDto.QuestionByMyAnswerAndReply result : results) {
            Announcement findAnnouncement = result.getAnnouncement();
            User findUser = result.getUser();
            AnswerResponseDto.MyWriteResponse myWriteResponse = AnswerResponseDto.MyWriteResponse.builder()
                    .announcementId(findAnnouncement.getId())
                    .announcementType(findAnnouncement.getAnnouncementType() == AnnouncementType.ON_CAMPUS ? "교내" : "교외")
                    .announcementName(findAnnouncement.getTitle())
                    .questionWrtierProfile(0) // 추후 유저 프로필 수정 필요
                    .questionWriterName(findUser.getNickname())
                    .questionContent(result.getContent())
                    .build();

            if (result.getWriteType().equals("Answer")) { // 내 답변인 경우
                // myAnswer 설정
                Optional<Answer> answer = answerRepository.findById(result.getAnswerOrReplyId());
                DefaultAssert.isTrue(answer.isPresent());
                Answer findAnswer = answer.get();
                AnswerResponseDto.MyAnswerResponse answerResponse = AnswerResponseDto.MyAnswerResponse.builder()
                        .answerContent(findAnswer.getContent())
                        .createdAt(findAnswer.getCreatedAt())
                        .heartCount(heartRepository.countByAnswerId(findAnswer.getId()))
                        .replyCount(replyRepository.countByAnswerId(findAnswer.getId()))
                        .build();
                myWriteResponse.setMyAnswer(answerResponse);
            } else {
                // myReply 설정
                Optional<Reply> reply = replyRepository.findById(result.getAnswerOrReplyId());
                DefaultAssert.isTrue(reply.isPresent());
                Reply findReply = reply.get();
                Answer findAnswer = findReply.getAnswer();
                List<Reply> replies = replyRepository.findAllByAnswerId(findAnswer.getId());
                List<AnswerResponseDto.ReplyList> replyLists = new ArrayList<>();
                for (Reply tempReply : replies) {
                    replyLists.add(AnswerResponseDto.ReplyList.builder()
                                    .isMine(tempReply.getUser().equals(user))
                                    .replyWriterProfile(0) // 추후 유저 프로필 수정 필요
                                    .replyWriterName(tempReply.getUser().getNickname())
                                    .replyContent(tempReply.getContent())
                                    .createdAt(tempReply.getCreatedAt())
                                    .heartCount(heartRepository.countByReplyId(tempReply.getId()))
                            .build());
                }
                AnswerResponseDto.MyReplyResponse replyResponse = AnswerResponseDto.MyReplyResponse.builder()
                        .answerWriterProfile(0) // 추후 유저 프로필 수정 필요
                        .answerWriterName(findAnswer.getUser().getNickname())
                        .answerContent(findAnswer.getContent())
                        .replyList(replyLists)
                        .build();
                myWriteResponse.setMyReply(replyResponse);
            }
            responses.add(myWriteResponse);
        }
        return responses;
    }
}
