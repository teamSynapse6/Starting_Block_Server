package com.startingblock.domain.announcement.domain.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.startingblock.domain.announcement.domain.*;
import com.startingblock.domain.announcement.dto.*;
import com.startingblock.domain.common.Status;
import com.startingblock.domain.roadmap.domain.QRoadmap;
import com.startingblock.domain.roadmap.domain.RoadmapStatus;
import com.startingblock.domain.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.startingblock.domain.announcement.domain.QAnnouncement.*;
import static com.startingblock.domain.announcement.domain.QLecture.*;
import static com.startingblock.domain.roadmap.domain.QRoadmap.*;
import static com.startingblock.domain.roadmap.domain.QRoadmapAnnouncement.*;
import static com.startingblock.domain.roadmap.domain.QRoadmapLecture.*;

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
    public List<AnnouncementRes> findThreeRandomAnnouncement(final Long userId) {
        return queryFactory
                .select(
                        new QAnnouncementRes(
                                announcement.id,
                                announcement.bizPrchDprtNm,
                                announcement.title,
                                Expressions.stringTemplate("COALESCE({0}, {1})", announcement.startDate.stringValue(), announcement.nonDate),
                                Expressions.stringTemplate("COALESCE({0}, {1})", announcement.endDate.stringValue(), announcement.nonDate),
                                roadmapAnnouncement.announcement.id.isNotNull(),
                                announcement.contact.isNotNull(),
                                announcement.isFileUploaded
                ))
                .from(announcement)
                .leftJoin(roadmapAnnouncement).on(announcement.id.eq(roadmapAnnouncement.announcement.id).and(roadmapAnnouncement.roadmap.user.id.eq(userId)))
                .where(
                        announcement.announcementType.in(AnnouncementType.OPEN_DATA, AnnouncementType.BIZ_INFO),
                        announcement.startDate.loe(LocalDateTime.now()).or(announcement.nonDate.isNotNull()), // 현재 날짜보다 이전이거나, 비기한이 없는 공고
                        announcement.endDate.goe(LocalDateTime.now()).or(announcement.nonDate.isNotNull()), // 현재 날짜보다 이후이거나, 비기한이 없는 공고
                        announcement.status.eq(Status.ACTIVE)
                )
                .distinct()
                .orderBy(Expressions.numberTemplate(Double.class, "function('rand')").asc())
                .limit(3)
                .fetch();
    }

    @Override
    public List<Announcement> findThreeRandomOnCampusAnnouncements(final University university) {
        return queryFactory
                .selectFrom(announcement)
                .where(
                        announcement.announcementType.eq(AnnouncementType.ON_CAMPUS),
                        announcement.university.eq(university)
                )
                .distinct()
                .orderBy(Expressions.numberTemplate(Double.class, "function('rand')").asc())
                .limit(3)
                .fetch();
    }

    @Override
    public AnnouncementDetailRes findAnnouncementDetail(final Long userId, final Long announcementId) {
        return queryFactory
                .select(
                        new QAnnouncementDetailRes(
                                announcement.id,
                                roadmapAnnouncement.announcement.id.isNotNull(),
                                announcement.bizPrchDprtNm,
                                announcement.title,
                                announcement.content,
                                announcement.startDate.stringValue(),
                                announcement.endDate.stringValue(),
                                announcement.postTarget,
                                announcement.postTargetComAge,
                                announcement.supportType,
                                announcement.detailUrl,
                                announcement.areaName,
                                announcement.roadmapCount,
                                announcement.announcementType.stringValue(),
                                announcement.contact,
                                announcement.contact.isNotNull(),
                                announcement.isFileUploaded
                        )
                )
                .from(announcement)
                .leftJoin(roadmapAnnouncement).on(announcement.id.eq(roadmapAnnouncement.announcement.id).and(roadmapAnnouncement.roadmap.user.id.eq(userId)))
                .where(
                        announcement.id.eq(announcementId),
                        announcement.status.eq(Status.ACTIVE)
                )
                .fetchOne();
    }

    @Override
    public Slice<AnnouncementRes> findAnnouncements(final Long userId, final Pageable pageable, final String businessAge, final String region, final String supportType, final String sort, final String search) {
        List<AnnouncementRes> announcementResList = queryFactory
                .select(
                        new QAnnouncementRes(
                                announcement.id,
                                announcement.bizPrchDprtNm,
                                announcement.title,
                                Expressions.stringTemplate("COALESCE({0}, {1})", announcement.startDate.stringValue(), announcement.nonDate),
                                Expressions.stringTemplate("COALESCE({0}, {1})", announcement.endDate.stringValue(), announcement.nonDate),
                                roadmapAnnouncement.announcement.id.isNotNull(),
                                announcement.contact.isNotNull(),
                                announcement.isFileUploaded
                        )
                )
                .from(announcement)
                .leftJoin(roadmapAnnouncement).on(announcement.id.eq(roadmapAnnouncement.announcement.id).and(roadmapAnnouncement.roadmap.user.id.eq(userId)))
                .where(
                        announcement.startDate.loe(LocalDateTime.now()).or(announcement.nonDate.isNotNull()), // 현재 날짜보다 이전이거나, 비기한이 없는 공고
                        announcement.endDate.goe(LocalDateTime.now()).or(announcement.nonDate.isNotNull()), // 현재 날짜보다 이후이거나, 비기한이 없는 공고
                        announcement.status.eq(Status.ACTIVE),
                        announcement.announcementType.eq(AnnouncementType.OPEN_DATA).or(announcement.announcementType.eq(AnnouncementType.BIZ_INFO)),
                        businessAgeExpression(businessAge),
                        regionExpression(region),
                        supportTypeExpression(supportType),
                        searchExpression(search)
                )
                .distinct()
                .orderBy(announcementOrderBy(sort))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize() + 1) // +1 해서 다음 페이지가 있는지 체크
                .fetch();

        boolean hasNext = announcementResList.size() > pageable.getPageSize();
        List<AnnouncementRes> announcements = hasNext ? announcementResList.subList(0, pageable.getPageSize()) : announcementResList;

        return new SliceImpl<>(announcements, pageable, hasNext);
    }

    @Override
    public List<Announcement> findListOfRoadmapByRoadmapId(final Long userId, final Long roadmapId, final String type) {
        return queryFactory
                .select(announcement)
                .from(roadmap)
                .leftJoin(roadmapAnnouncement).on(roadmap.id.eq(roadmapAnnouncement.roadmap.id))
                .leftJoin(announcement).on(roadmapAnnouncement.announcement.id.eq(announcement.id))
                .where(
                        announcement.startDate.loe(LocalDateTime.now()).or(announcement.nonDate.isNotNull()), // 현재 날짜보다 이전이거나, 비기한이 없는 공고
                        announcement.endDate.goe(LocalDateTime.now()).or(announcement.nonDate.isNotNull()), // 현재 날짜보다 이후이거나, 비기한이 없는 공고
                        roadmap.id.eq(roadmapId),
                        roadmap.user.id.eq(userId),
                        createExpressionForAnnouncementType(type)
                )
                .fetch();
    }

    @Override
    public List<OnCampusAnnouncementRes> findOnCampusAnnouncements(final Long userId, final University university, final String search, final Keyword keyword) {
        return queryFactory
                .select(
                    new QOnCampusAnnouncementRes(
                            announcement.id,
                            announcement.keyword.stringValue(),
                            announcement.title,
                            announcement.insertDate,
                            announcement.detailUrl,
                            roadmapAnnouncement.announcement.id.isNotNull()
                    )
                )
                .from(announcement)
                .leftJoin(roadmapAnnouncement).on(roadmapAnnouncement.announcement.eq(announcement).and(roadmapAnnouncement.roadmap.user.id.eq(userId)))
                .where(
                        announcement.announcementType.eq(AnnouncementType.ON_CAMPUS),
                        announcement.university.eq(university),
                        searchExpression(search),
                        keywordExpression(keyword)
                )
                .distinct()
                .fetch();
    }

    @Override
    public List<SystemRes> findSystems(final Long userId, final University university) {
        return queryFactory
                .select(
                        new QSystemRes(
                                announcement.id,
                                announcement.title,
                                announcement.postTarget,
                                announcement.content,
                                roadmapAnnouncement.announcement.id.isNotNull()
                        )
                )
                .from(announcement)
                .leftJoin(roadmapAnnouncement).on(roadmapAnnouncement.announcement.eq(announcement).and(roadmapAnnouncement.roadmap.user.id.eq(userId)))
                .where(
                        announcement.announcementType.eq(AnnouncementType.SYSTEM),
                        announcement.university.eq(university)
                )
                .distinct()
                .fetch();
    }

    @Override
    public List<LectureRes> findLectures(final Long id, final University university) {
        return queryFactory
                .select(
                        new QLectureRes(
                                lecture.id,
                                lecture.title,
                                lecture.liberal,
                                lecture.credit,
                                lecture.session,
                                lecture.instructor,
                                lecture.content,
                                roadmapLecture.lecture.id.isNotNull()
                        )
                )
                .from(lecture)
                .leftJoin(roadmapLecture).on(roadmapLecture.lecture.eq(lecture).and(roadmapLecture.roadmap.user.id.eq(id)))
                .where(
                        lecture.university.eq(university)
                )
                .distinct()
                .fetch();
    }

    @Override
    public List<Announcement> findSupportGroups(final Long id, final University university, final Keyword keyword) {
        return queryFactory
                .selectFrom(announcement)
                .where(
                        announcement.announcementType.eq(AnnouncementType.SUPPORT_GROUP),
                        announcement.university.eq(university),
                        keywordExpression(keyword)
                )
                .distinct()
                .fetch();
    }

    @Override
    public List<String> findSupportGroupKeywords(University university) {
        return queryFactory
                .select(announcement.keyword.stringValue())
                .from(announcement)
                .where(
                        announcement.announcementType.eq(AnnouncementType.SUPPORT_GROUP),
                        announcement.university.eq(university)
                )
                .distinct()
                .fetch();
    }

    @Override
    public List<Announcement> findOffCampusAnnouncementsBySupportType(final String supportType) {
        return queryFactory
                .selectFrom(announcement)
                .where(
                        announcement.announcementType.in(AnnouncementType.OPEN_DATA, AnnouncementType.BIZ_INFO),
                        announcement.supportType.contains(supportType),
                        announcement.startDate.loe(LocalDateTime.now()).or(announcement.nonDate.isNotNull()),
                        announcement.endDate.goe(LocalDateTime.now()).or(announcement.nonDate.isNotNull())
                )
                .distinct()
                .fetch();
    }

    @Override
    public List<Announcement> findCustomAnnouncementOff(final User user) {
        // 첫 번째 쿼리: 조건에 맞는 공고 먼저 조회
        List<Announcement> announcements = queryFactory
                .selectFrom(announcement)
                .leftJoin(roadmapAnnouncement).on(roadmapAnnouncement.announcement.eq(announcement))
                .where(announcement.announcementType.in(AnnouncementType.OPEN_DATA, AnnouncementType.BIZ_INFO)
                        .and(
                                isUserAreaAndSupportTypeMatch(user)
                                        .or(announcementSupportTypeMatchesRoadmapTitle(user))
                        )
                        .and(
                                announcement.endDate.isNull()
                                        .or(announcement.endDate.after(LocalDateTime.now()))
                        ))
                .groupBy(announcement.id)
                .orderBy(roadmapAnnouncement.count().desc(), announcement.createdAt.desc())
                .limit(2)
                .fetch();

        // 첫 번째 쿼리 결과가 충분하지 않은 경우, 추가 쿼리 실행
        if (announcements.size() < 2) {
            List<Announcement> additionalAnnouncements = queryFactory
                    .selectFrom(announcement)
                    .leftJoin(roadmapAnnouncement).on(roadmapAnnouncement.announcement.eq(announcement))
                    .where(announcement.announcementType.in(AnnouncementType.OPEN_DATA, AnnouncementType.BIZ_INFO),
                            announcement.id.notIn(announcements.stream().map(Announcement::getId).collect(Collectors.toSet()))
                                    .and(
                                            announcement.endDate.isNull()
                                                    .or(announcement.endDate.after(LocalDateTime.now()))
                                    ))
                    .groupBy(announcement.id)
                    .orderBy(roadmapAnnouncement.count().desc(), announcement.createdAt.desc())
                    .limit(2 - announcements.size())
                    .fetch();
            announcements.addAll(additionalAnnouncements);
        }

        return announcements;
    }

    @Override
    public List<Announcement> findCustomAnnouncementOnOff(final User user, final University university) {
        // BIZ_INFO or OPEN_DATA
        Announcement offCampusAnnouncement = queryFactory
                .selectFrom(announcement)
                .leftJoin(roadmapAnnouncement).on(roadmapAnnouncement.announcement.eq(announcement))
                .where(announcement.announcementType.in(AnnouncementType.OPEN_DATA, AnnouncementType.BIZ_INFO)
                        .and(
                                isUserAreaAndSupportTypeMatch(user)
                                        .or(announcementSupportTypeMatchesRoadmapTitle(user))
                        )
                        .and(
                                announcement.endDate.isNull()
                                        .or(announcement.endDate.after(LocalDateTime.now()))
                        ))
                .groupBy(announcement.id)
                .orderBy(roadmapAnnouncement.count().desc(), announcement.createdAt.desc())
                .limit(1)
                .fetchOne();

        // ON_CAMPUS
        Announcement onCampusAnnouncement = queryFactory
                .selectFrom(announcement)
                .leftJoin(roadmapAnnouncement).on(roadmapAnnouncement.announcement.eq(announcement))
                .where(announcement.announcementType.eq(AnnouncementType.ON_CAMPUS),
                        announcement.university.eq(university))
                .groupBy(announcement.id)
                .orderBy(roadmapAnnouncement.count().desc(), announcement.createdAt.desc())
                .limit(1)
                .fetchOne();

        return Stream.of(offCampusAnnouncement, onCampusAnnouncement)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<Announcement> findOffCampusAnnouncementsByRoadmapCount() {
        return queryFactory
                .selectFrom(announcement)
                .where(
                        announcement.announcementType.in(AnnouncementType.OPEN_DATA, AnnouncementType.BIZ_INFO),
                        announcement.startDate.loe(LocalDateTime.now()).or(announcement.nonDate.isNotNull()),
                        announcement.endDate.goe(LocalDateTime.now()).or(announcement.nonDate.isNotNull()),
                        announcement.roadmapCount.gt(0)
                )
                .orderBy(announcement.roadmapCount.desc())
                .limit(3)
                .fetch();
    }

    @Override
    public List<Announcement> findOffCampusAnnouncementsByInsertDate() {
        return queryFactory
                .selectFrom(announcement)
                .where(
                        announcement.announcementType.in(AnnouncementType.OPEN_DATA, AnnouncementType.BIZ_INFO),
                        announcement.startDate.loe(LocalDateTime.now()).or(announcement.nonDate.isNotNull()),
                        announcement.endDate.goe(LocalDateTime.now()).or(announcement.nonDate.isNotNull())
                )
                .orderBy(announcement.startDate.desc())
                .limit(3)
                .fetch();
    }

    @Override
    public RoadmapSystemRes findRandomSystemByUniversity(Long id, University university) {
        return queryFactory
                .select(
                        new QRoadmapSystemRes(
                                announcement.id,
                                announcement.title,
                                announcement.postTarget,
                                announcement.content,
                                roadmapAnnouncement.announcement.id.isNotNull()
                        )
                )
                .from(announcement)
                .leftJoin(roadmapAnnouncement).on(roadmapAnnouncement.announcement.eq(announcement).and(roadmapAnnouncement.roadmap.user.id.eq(id)))
                .where(
                        announcement.announcementType.eq(AnnouncementType.SYSTEM),
                        announcement.university.eq(university)
                )
                .distinct()
                .orderBy(Expressions.numberTemplate(Double.class, "function('rand')").asc())
                .limit(1)
                .fetchOne();
    }

    private BooleanExpression keywordExpression(final Keyword keyword) {
        if (keyword == null) return null;
        return announcement.keyword.eq(keyword);
    }

    private BooleanExpression createExpressionForAnnouncementType(final String type) {
        return switch (type) {
            case "ON-CAMPUS" -> announcement.announcementType.eq(AnnouncementType.ON_CAMPUS);
            case "OFF-CAMPUS" -> announcement.announcementType.in(AnnouncementType.OPEN_DATA, AnnouncementType.BIZ_INFO);
            case "SYSTEM" -> announcement.announcementType.eq(AnnouncementType.SYSTEM);
            default -> null;
        };
    }

    private BooleanExpression businessAgeExpression(final String businessAge) {
        if (businessAge == null) return null;

        List<String> conditions = Arrays.asList("예비창업자", "1년미만", "2년미만", "3년미만", "5년미만", "7년미만", "10년미만");
        BooleanExpression expression = null;

        for (String age : conditions) {
            if (!age.equals(businessAge)) {
                expression = (expression == null) ? createExpressionForBusinessAge(age) : expression.or(createExpressionForBusinessAge(age));
            } else {
                expression = (expression == null) ? createExpressionForBusinessAge(age) : expression.or(createExpressionForBusinessAge(age));
                break;
            }
        }

        return expression;
    }

    private BooleanExpression regionExpression(final String region) {
        if (region == null) return null;
        return createExpressionForRegion(region);
    }

    private BooleanExpression supportTypeExpression(final String supportType) {
        if (supportType == null) return null;
        String[] supportTypes = supportType.split(",");

        BooleanExpression expression = null;
        for (String type : supportTypes) {
            expression = (expression == null) ? announcement.supportType.contains(type.trim()) : expression.or(announcement.supportType.contains(type.trim()));
        }

        return expression;
    }

    private BooleanExpression searchExpression(final String search) {
        if (search == null) return null;
        return announcement.title.contains(search);
    }

    private OrderSpecifier<?> announcementOrderBy(final String sort) {
        if (sort == null) return announcement.startDate.desc();

        return switch (sort) {
            case "로드맵에 저장 많은 순" -> announcement.roadmapCount.desc();
            case "최신순" -> announcement.startDate.desc().nullsLast();
            default -> announcement.startDate.desc();
        };
    }

    private BooleanExpression createExpressionForBusinessAge(final String businessAge) {
        return switch (businessAge) {
            case "예비창업자" -> announcement.postTargetComAge.contains("예비창업자");
            case "1년미만" -> announcement.postTargetComAge.contains("1년미만");
            case "2년미만" -> announcement.postTargetComAge.contains("2년미만");
            case "3년미만" -> announcement.postTargetComAge.contains("3년미만");
            case "5년미만" -> announcement.postTargetComAge.contains("5년미만");
            case "7년미만" -> announcement.postTargetComAge.contains("7년미만");
            case "10년미만" -> announcement.postTargetComAge.contains("10년미만");
            default -> null;
        };
    }

    private BooleanExpression createExpressionForRegion(final String region) {
        return switch (region) {
            case "서울" -> announcement.areaName.contains("서울").or(announcement.title.contains("서울"));
            case "경기" -> announcement.areaName.contains("경기").or(announcement.title.contains("경기"));
            case "인천" -> announcement.areaName.contains("인천").or(announcement.title.contains("인천"));
            case "강원" -> announcement.areaName.contains("강원").or(announcement.title.contains("강원"));
            case "충북" -> announcement.areaName.contains("충북").or(announcement.areaName.contains("충청북도")).or(announcement.title.contains("충북"));
            case "충남" -> announcement.areaName.contains("충남").or(announcement.areaName.contains("충청남도")).or(announcement.title.contains("충남"));
            case "대전" -> announcement.areaName.contains("대전").or(announcement.title.contains("대전"));
            case "경북" -> announcement.areaName.contains("경북").or(announcement.areaName.contains("경상북도")).or(announcement.title.contains("경북"));
            case "경남" -> announcement.areaName.contains("경남").or(announcement.areaName.contains("경상남도")).or(announcement.title.contains("경남"));
            case "대구" -> announcement.areaName.contains("대구").or(announcement.title.contains("대구"));
            case "울산" -> announcement.areaName.contains("울산").or(announcement.title.contains("울산"));
            case "부산" -> announcement.areaName.contains("부산").or(announcement.title.contains("부산"));
            case "전북" -> announcement.areaName.contains("전북").or(announcement.areaName.contains("전라북도")).or(announcement.title.contains("전북"));
            case "전남" -> announcement.areaName.contains("전남").or(announcement.areaName.contains("전라남도")).or(announcement.title.contains("전남"));
            case "광주" -> announcement.areaName.contains("광주").or(announcement.title.contains("광주"));
            case "제주" -> announcement.areaName.contains("제주").or(announcement.title.contains("제주"));
            default -> null;
        };
    }

    private BooleanExpression isUserAreaAndSupportTypeMatch(User user) {
        return announcement.areaName.eq(user.getResidence());
    }

    private BooleanExpression announcementSupportTypeMatchesRoadmapTitle(final User user) {
        QRoadmap roadmap = QRoadmap.roadmap;

        JPAQuery<?> subQuery = new JPAQuery<Void>()
                .select(roadmap.id)
                .from(roadmap)
                .where(roadmap.user.eq(user),
                        roadmap.title.eq(announcement.supportType),
                        roadmap.roadmapStatus.eq(RoadmapStatus.IN_PROGRESS));

        return Expressions.booleanTemplate("exists {0}", subQuery);
    }

}
