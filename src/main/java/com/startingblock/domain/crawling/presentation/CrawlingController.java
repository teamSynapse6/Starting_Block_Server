package com.startingblock.domain.crawling.presentation;

import com.startingblock.domain.crawling.application.CrawlingService;
import com.startingblock.global.config.security.token.CurrentUser;
import com.startingblock.global.config.security.token.UserPrincipal;
import com.startingblock.global.payload.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Crawling V1 API", description = "Crawling V1 API")
@RequestMapping("/api/v1/crawling")
@RestController
@RequiredArgsConstructor
public class CrawlingController {

    private final CrawlingService crawlingService;

    @Operation(summary = "교내 크롤링 초기 저장", description = "교내 크롤링 초기 저장")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "교내 크롤링 초기 저장 성공", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "400", description = "교내 크롤링 초기 저장 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @PostMapping("/on-campus/initial")
    public ResponseEntity<?> onCampusInitialCrawling(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal
    ) {
        crawlingService.onCampusInitialCrawling();
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "k-startup 이메일 크롤링", description = "k-startup 이메일 크롤링")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "k-startup 이메일 크롤링 성공", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "400", description = "k-startup 이메일 크롤링 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @PatchMapping("/off-campus/email")
    public ResponseEntity<?> offCampusEmailCrawling(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal
    ) {
        crawlingService.offCampusEmailCrawling();
        return ResponseEntity.noContent().build();
    }
}
