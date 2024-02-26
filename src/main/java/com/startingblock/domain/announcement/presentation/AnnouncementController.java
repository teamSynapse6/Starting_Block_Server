package com.startingblock.domain.announcement.presentation;


import com.startingblock.domain.announcement.application.AnnouncementService;
import com.startingblock.domain.announcement.dto.AnnouncementRes;
import com.startingblock.global.config.security.token.CurrentUser;
import com.startingblock.global.config.security.token.UserPrincipal;
import com.startingblock.global.payload.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@Tag(name = "Announcements API", description = "Announcements API")
@RequestMapping("/api/v1/announcements")
@RestController
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @Operation(summary = "공고 새로고침", description = "공고 새로고침")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "공고 새로고침 성공", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "400", description = "공고 새로고침 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @GetMapping("/refresh")
    public ResponseEntity<Void> refreshAnnouncements() {
        return ResponseEntity.ok(announcementService.refreshAnnouncements());
    }

    @Operation(summary = "공고 조건별 검색(Paging)", description = "공고 조건별 검색(Paging)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "공고 조건별 검색 성공", content = {@Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = AnnouncementRes.class)))}),
            @ApiResponse(responseCode = "400", description = "공고 조건별 검색 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @GetMapping("/list")
    public ResponseEntity<Slice<AnnouncementRes>> findAnnouncements(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @Parameter(name = "pageable", description = "조회 할 페이지와 페이지 크기를 입력해주세요(sort는 무시해도 됩니다. + Page는 0번부터 시작)") final Pageable pageable,
            @Parameter(name = "businessAge", description = "사업자형태") @RequestParam(required = false) final String businessAge,
            @Parameter(name = "region", description = "지역") @RequestParam(required = false) final String region,
            @Parameter(name = "supportType", description = "지원 분야") @RequestParam(required = false) final String supportType,
            @Parameter(name = "sort", description = "최신순, 로드맵에 저장 많은 순") @RequestParam(required = false) final String sort,
            @Parameter(name = "search", description = "검색어") @RequestParam(required = false) final String search
    ) {
        return ResponseEntity.ok(announcementService.findAnnouncements(userPrincipal, pageable, businessAge, region, supportType, sort, search));
    }

}