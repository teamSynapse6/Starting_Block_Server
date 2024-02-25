package com.startingblock.domain.announcement.domain.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.startingblock.domain.announcement.dto.AnnouncementRes;
import com.startingblock.domain.announcement.dto.QAnnouncementRes;
import com.startingblock.domain.common.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.time.LocalDateTime;
import java.util.List;

import static com.startingblock.domain.announcement.domain.QAnnouncement.*;
import static com.startingblock.domain.bookmark.domain.QAnnouncementBookmark.*;

@RequiredArgsConstructor
public class AnnouncementQuerydslRepositoryImpl implements AnnouncementQuerydslRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<String> findAnnouncementPostIds() {
        return queryFactory
                .select(announcement.postSN)
                .from(announcement)
                .fetch();
    }

    @Override
    public Slice<AnnouncementRes> findAnnouncements(final Long userId, final Pageable pageable, final String businessAge, final String region, final String supportType, final String sort, final String search) {
//        List<AnnouncementRes> announcementResList = queryFactory
//                .select(
//                        new QAnnouncementRes(
//                                announcement.id,
//                                announcement.bizPrchDprtNm,
//                                announcement.title,
//                                Expressions.stringTemplate("COALESCE({0}, {1})", announcement.startDate.stringValue(), announcement.nonDate),
//                                Expressions.stringTemplate("COALESCE({0}, {1})", announcement.endDate.stringValue(), announcement.nonDate),
//                                announcementBookmark.id.isNotNull()
//                        )
//                )
//                .from(announcement)
//                .leftJoin(announcementBookmark).on(announcement.id.eq(announcementBookmark.announcement.id).and(announcementBookmark.user.id.eq(userId)))
//                .where(
//                        announcement.endDate.goe(LocalDateTime.now()),
//                        announcement.status.eq(Status.ACTIVE)
//                )
//                .fetch();
        return null;
    }

//    private BooleanExpression getBusinessAgeExpression(final String businessAge) {
//        BooleanExpression expression = null;
//        String[] businessAges ={"예비창업자", "1년미만", "2년미만", "3년미만", "5년미만", "7년미만", "10년미만"};
//
//        if(businessAge.equals("예비창업자")) {
//
//        }
//    }

}
