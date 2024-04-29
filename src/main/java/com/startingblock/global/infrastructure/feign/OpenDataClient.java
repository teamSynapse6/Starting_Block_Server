package com.startingblock.global.infrastructure.feign;

import com.startingblock.global.config.FeignConfig;
import com.startingblock.global.infrastructure.feign.dto.KStartUpAnnouncementRes;
import com.startingblock.global.infrastructure.feign.dto.NewKStartUpAnnouncementRes;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "open-data", url = "https://apis.data.go.kr", configuration = FeignConfig.class)
@Component
public interface OpenDataClient {

    @GetMapping("/B552735/k-startup/kisedGWAPI/getAnnouncementList")
    KStartUpAnnouncementRes getAnnouncementList(final @RequestParam("serviceKey") String serviceKey,
                                                final @RequestParam("pageNo") String page,
                                                final @RequestParam("numOfRows") String numOfRows,
                                                final @RequestParam("startDate") String startDate,
                                                final @RequestParam("endDate") String endDate,
                                                final @RequestParam("openYn") String openYn,
                                                final @RequestParam("dataType") String dataType);

    @GetMapping("/B552735/kisedKstartupService/getAnnouncementInformation")
    NewKStartUpAnnouncementRes getNewAnnouncementList(final @RequestParam("serviceKey") String serviceKey,
                                                     final @RequestParam("page") String page,
                                                     final @RequestParam("perPage") String perPage,
                                                     final @RequestParam("returnType") String returnType);

}
