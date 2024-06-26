package com.startingblock.domain.reply.domain.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.startingblock.domain.heart.domain.QHeart;
import com.startingblock.domain.reply.domain.QReply;
import com.startingblock.domain.reply.dto.ReplyResponseDto;
import com.startingblock.domain.user.domain.QUser;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class ReplyQuerydslRepositoryImpl implements ReplyQuerydslRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ReplyResponseDto.ReplyResponse> findReplyListByAnswerId(Long answerId, Long userId) {
        QReply reply = QReply.reply;
        QUser user = QUser.user;
        QHeart heart = QHeart.heart;

        return queryFactory
                .select(Projections.constructor(ReplyResponseDto.ReplyResponse.class,
                        reply.id,
                        JPAExpressions.selectOne()
                                .from(reply)
                                .where(reply.user.id.eq(userId))
                                .exists(),
                        user.nickname,
                        user.profileNumber,
                        reply.content,
                        reply.createdAt,
                        JPAExpressions.select(heart.count())
                                .from(heart)
                                .where(heart.reply.id.eq(reply.id)),
                        JPAExpressions.selectOne()
                                .from(heart)
                                .where(heart.reply.id.eq(reply.id)
                                        .and(heart.user.id.eq(userId)))
                                .exists(),
                        JPAExpressions.select(heart.id.max())
                                .from(heart)
                                .where(heart.user.id.eq(userId).and(heart.reply.id.eq(reply.id)))))
                .from(reply)
                .join(reply.user, user)
                .where(reply.answer.id.eq(answerId))
                .fetch();
    }
}
