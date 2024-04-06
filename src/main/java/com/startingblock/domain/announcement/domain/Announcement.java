package com.startingblock.domain.announcement.domain;

import com.startingblock.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "announcement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@ToString
public class Announcement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_sn", nullable = false, unique = true)
    private String postSN; // 게시물번호

    @Column(name = "biz_title")
    private String bizTitle; // 지원사업분류

    @Column(name = "support_type")
    private String supportType; // 지원분야

    @Column(name = "title")
    private String title; // 제목

    @Column(name = "content")
    private String content; // 내용

    @Column(name = "file_url")
    private String fileUrl; // 첨부파일

    @Column(name = "area_name")
    private String areaName; // 지역

    @Column(name = "organization_name")
    private String organizationName; // 소속

    @Column(name = "post_target")
    private String postTarget; // 대상

    @Column(name = "post_target_age")
    private String postTargetAge; // 대상연령

    @Column(name = "post_target_com_age")
    private String postTargetComAge; // 대상업력

    @Column(name = "start_date")
    private LocalDateTime startDate; // 접수시작일자

    @Column(name = "end_date")
    private LocalDateTime endDate; // 접수종료일자

    @Column(name = "insert_date")
    private LocalDateTime insertDate; // 등록일자

    @Column(name = "non_date")
    private String nonDate;

    @Column(name = "detail_url")
    @Lob
    private String detailUrl; // 상세 URL

    @Column(name = "prch_cn_adr_no")
    private String prchCnAdrNo; // 연락처

    @Column(name = "contact")
    private String contact; // 이메일

    @Column(name = "sprv_inst_clss_cd_nm")
    private String sprvInstClssCdNm; // 기관구분

    @Column(name = "biz_prch_dprt_nm")
    private String bizPrchDprtNm; // 담당부서

    @Column(name = "blng_gv_dp_cd_nm")
    private String blngGvDpCdNm; // 소관부처

    @Enumerated(EnumType.STRING)
    @Column(name = "announcement_type")
    private AnnouncementType announcementType;

    @Column(name = "roadmap_count")
    private Integer roadmapCount;

    @Column(name = "is_file_used")
    private Boolean isFileUsed;

    public void addRoadmapCount() {
        this.roadmapCount++;
    }

    public void subtractRoadmapCount() {
        this.roadmapCount--;
    }

    @Builder
    public Announcement(String postSN, String fileUrl, String bizTitle, String supportType, String title, String content, String areaName, String organizationName, String postTarget, String postTargetAge, String postTargetComAge, LocalDateTime startDate, LocalDateTime endDate, LocalDateTime insertDate, String nonDate, String detailUrl, String prchCnAdrNo, String contact, String sprvInstClssCdNm, String bizPrchDprtNm, String blngGvDpCdNm, AnnouncementType announcementType) {
        this.postSN = postSN;
        this.bizTitle = bizTitle;
        this.fileUrl = fileUrl;
        this.supportType = supportType;
        this.title = title;
        this.content = content;
        this.areaName = areaName;
        this.organizationName = organizationName;
        this.postTarget = postTarget;
        this.postTargetAge = postTargetAge;
        this.postTargetComAge = postTargetComAge;
        this.startDate = startDate;
        this.endDate = endDate;
        this.insertDate = insertDate;
        this.nonDate = nonDate;
        this.detailUrl = detailUrl;
        this.prchCnAdrNo = prchCnAdrNo;
        this.contact = contact;
        this.sprvInstClssCdNm = sprvInstClssCdNm;
        this.bizPrchDprtNm = bizPrchDprtNm;
        this.blngGvDpCdNm = blngGvDpCdNm;
        this.announcementType = announcementType;
        this.isFileUsed = false;
        this.roadmapCount = 0;
    }

}
