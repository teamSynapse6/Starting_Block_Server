package com.startingblock.global.infrastructure.feign.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class NewKStartUpAnnouncementRes {

    private Integer currentCount;
    private List<Item> data;
    private Integer matchCount;
    private Integer page;
    private Integer perPage;
    private Integer totalCount;

    @Data
    public static class Item {
        @JsonProperty("aply_excl_trgt_ctnt")
        private String aplyExclTrgtCtnt;
        @JsonProperty("aply_mthd_etc_istc")
        private String aplyMthdEtcIstc;
        @JsonProperty("aply_mthd_fax_rcpt_istc")
        private String aplyMthdFaxRcptIstc;
        @JsonProperty("aply_mthd_onli_rcpt_istc")
        private String aplyMthdOnliRcptIstc;
        @JsonProperty("aply_mthd_pssr_rcpt_istc")
        private String aplyMthdPssrRcptIstc;
        @JsonProperty("aply_mthd_vst_rcpt_istc")
        private String aplyMthdVstRcptIstc;
        @JsonProperty("aply_trgt")
        private String aplyTrgt;
        @JsonProperty("aply_trgt_ctnt")
        private String aplyTrgtCtnt;
        @JsonProperty("biz_aply_url")
        private String bizAplyUrl;
        @JsonProperty("biz_enyy")
        private String bizEnyy;
        @JsonProperty("biz_gdnc_url")
        private String bizGdncUrl;
        @JsonProperty("biz_pbanc_nm")
        private String bizPbancNm;
        @JsonProperty("biz_prch_dprt_nm")
        private String bizPrchDprtNm;
        @JsonProperty("biz_trgt_age")
        private String bizTrgtAge;
        @JsonProperty("detl_pg_url")
        private String detlPgUrl;
        @JsonProperty("id")
        private String id;
        @JsonProperty("intg_pbanc_biz_nm")
        private String intgPbancBizNm;
        @JsonProperty("intg_pbanc_yn")
        private String intgPbancYn;
        @JsonProperty("pbanc_ctnt")
        private String pbancCtnt;
        @JsonProperty("pbanc_ntrp_nm")
        private String pbancNtrpNm;
        @JsonProperty("pbanc_rcpt_bgng_dt")
        private String pbancRcptBgngDt;
        @JsonProperty("pbanc_rcpt_end_dt")
        private String pbancRcptEndDt;
        @JsonProperty("prch_cnpl_no")
        private String prchCnplNo;
        @JsonProperty("prfn_matr")
        private String prfnMatr;
        @JsonProperty("rcrt_prgs_yn")
        private String rcrtPrgsYn;
        @JsonProperty("sprv_inst")
        private String sprvInst;
        @JsonProperty("supt_biz_clsfc")
        private String suptBizClsfc;
        @JsonProperty("supt_regin")
        private String suptRegin;
    }

}
