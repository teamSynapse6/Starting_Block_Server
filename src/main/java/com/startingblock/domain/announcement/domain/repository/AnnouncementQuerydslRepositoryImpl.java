package com.startingblock.domain.announcement.domain.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.startingblock.domain.announcement.domain.QAnnouncement;
import com.startingblock.domain.common.Status;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.startingblock.domain.announcement.domain.QAnnouncement.*;

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

}
