package com.startingblock.global.infrastructure.feign;

import com.startingblock.global.infrastructure.feign.dto.KStartUpAnnouncementRes;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "open-data", url = "https://apis.data.go.kr")
@Component
public interface OpenDataClient {

    @GetMapping("/B552735/k-startup/kisedGWAPI/getAnnouncementList")
    KStartUpAnnouncementRes getAnnouncementList(@RequestParam("serviceKey") String serviceKey,
                                                @RequestParam("pageNo") int pageNo,
                                                @RequestParam("numOfRows") int numOfRows,
                                                @RequestParam("startDate") String startDate,
                                                @RequestParam("endDate") String endDate,
                                                @RequestParam("openYn") String openYn,
                                                @RequestParam("dataType") String dataType
                                                );

}
