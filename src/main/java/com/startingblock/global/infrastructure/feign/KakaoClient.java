package com.startingblock.global.infrastructure.feign;

import com.startingblock.global.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "kakao", url = "https://kapi.kakao.com", configuration = FeignConfig.class)
@Component
public interface KakaoClient {

    @PostMapping(value = "/v1/user/unlink", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    void unlinkUser(@RequestHeader(name = "Authorization") String authorization,
                    @RequestParam("target_id_type") String targetIdType,
                    @RequestParam("target_id") String targetId);

}
