package com.startingblock.global.infrastructure.feign.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Lob;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class KStartUpAnnouncementRes {

    private Response response;

    @Data
    public static class Response {
        private Header header;
        private Body body;

        @Data
        public static class Header {
            private String resultCode;
            private String resultMsg;
            // Getter와 Setter
        }

        @Data
        public static class Body {
            private String pageNo;
            private String totalCount;
            private List<Item> items;
            private String numOfRows;

            @Data
            public static class Item {
                private Long postSN; // 게시물번호
                private String bizTitle; // 지원사업분류
                private String supportType; // 지원분야
                private String title; // 제목
                private String areaName; // 지역
                private String organizationName; // 소속
                private String postTarget; // 대상
                private String postTargetAge; // 대상연령
                private String postTargetComAge; // 대상업력
                private LocalDateTime startDate; // 접수시작일자
                private LocalDateTime endDate; // 접수종료일자
                private LocalDateTime insertDate; // 등록일자
                private String detailUrl; // 상세 URL
                private String prchCnAdrNo; // 연락처
                private String sprvInstClssCdNm; // 기관구분
                private String bizPrchDprtNm; // 담당부서
                private String blngGvDpCdNm; // 소관부처
            }

        }

    }

}

