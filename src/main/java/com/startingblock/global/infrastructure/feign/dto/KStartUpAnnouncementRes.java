package com.startingblock.global.infrastructure.feign.dto;

import lombok.Data;
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
        }

        @Data
        public static class Body {
            private String pageNo;
            private String totalCount;
            private List<ItemWrapper> items;
            private String numOfRows;

            @Data
            public static class ItemWrapper {
                private Item item;
            }

            @Data
            public static class Item {
                private String postsn; // 게시물번호
                private String biztitle; // 지원사업분류
                private String supporttype; // 지원분야
                private String title; // 제목
                private String areaname; // 지역
                private String organizationname; // 소속
                private String posttarget; // 대상
                private String posttargetage; // 대상연령
                private String posttargetcomage; // 대상업력
                private String startdate; // 접수시작일자
                private String enddate; // 접수종료일자)
                private String insertdate; // 등록일자
                private String detailurl; // 상세 URL
                private String prchCnadrNo; // 연락처
                private String sprvInstClssCdNm; // 기관구분
                private String bizPrchDprtNm; // 담당부서
                private String blngGvdpCdNm; // 소관부처
            }
        }
    }
}