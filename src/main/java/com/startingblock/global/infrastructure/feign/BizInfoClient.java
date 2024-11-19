package com.startingblock.global.infrastructure.feign;

import com.startingblock.global.config.FeignConfig;
import com.startingblock.global.infrastructure.feign.dto.BizInfoAnnouncementRes;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "biz-info", url = "https://www.bizinfo.go.kr", configuration = FeignConfig.class)
@Component
public interface BizInfoClient {

    @GetMapping("/uss/rss/bizinfoApi.do")
    BizInfoAnnouncementRes getAnnouncementList(
            @RequestParam("crtfcKey") String crtfcKey,
            @RequestParam("dataType") String dataType,
            @RequestParam("searchCnt") int searchCnt
    );

}
