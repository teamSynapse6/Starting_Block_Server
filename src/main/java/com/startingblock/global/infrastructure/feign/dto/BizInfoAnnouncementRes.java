package com.startingblock.global.infrastructure.feign.dto;

import lombok.Data;

import java.util.List;

@Data
public class BizInfoAnnouncementRes {

    private List<Item> jsonArray;

    @Data
    public static class Item {
        private Integer totCnt;
        private Integer inqireCo;
        private String rceptEngnHmpgUrl;
        private String pblancUrl;
        private String jrsdInsttNm;
        private String printFlpthNm;
        private String pldirSportRealmLclasCodeNm;
        private String trgetNm;
        private String bsnsSumryCn;
        private String flpthNm;
        private String reqstBeginEndDe;
        private String printFileNm;
        private String reqstMthPapersCn;
        private String pldirSportRealmMlsfcCodeNm;
        private String excInsttNm;
        private String refrncNm;
        private String pblancNm;
        private String hashtags;
        private String fileNm;
        private String creatPnttm;
        private String pblancId;
    }

}
