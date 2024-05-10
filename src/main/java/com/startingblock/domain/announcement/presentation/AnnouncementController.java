package com.startingblock.domain.announcement.presentation;


import com.startingblock.domain.announcement.application.AnnouncementService;
import com.startingblock.domain.announcement.dto.AnnouncementDetailRes;
import com.startingblock.domain.announcement.dto.AnnouncementRes;
import com.startingblock.domain.announcement.dto.CustomAnnouncementRes;
import com.startingblock.domain.announcement.dto.SystemRes;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Tag(name = "Announcements V1 API", description = "Announcements V1 API")
@RequestMapping("/api/v1/announcements")
@RestController
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @Operation(summary = "공고 수동 새로고침", description = "공고 수동 새로고침")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "공고 수동 새로고침 성공", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "400", description = "공고 수동 새로고침 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @GetMapping("/refresh")
    public ResponseEntity<Void> refreshAnnouncements(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal
    ) {
        announcementService.refreshAnnouncementsV1(userPrincipal);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "교외 공고 조건별 검색(Paging)", description = "교외 공고 조건별 검색(Paging)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "교외 공고 조건별 검색 성공", content = {@Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = AnnouncementRes.class)))}),
            @ApiResponse(responseCode = "400", description = "교외 공고 조건별 검색 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @GetMapping("/list")
    public ResponseEntity<Slice<AnnouncementRes>> findAnnouncements(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @Parameter(name = "pageable", description = "조회 할 페이지와 페이지 크기를 입력해주세요(sort는 무시해도 됩니다. + Page는 0번부터 시작)") final Pageable pageable,
            @Parameter(name = "postTarget", description = "사업자형태") @RequestParam(required = false) final String postTarget,
            @Parameter(name = "region", description = "지역") @RequestParam(required = false) final String region,
            @Parameter(name = "supportType", description = "지원 분야") @RequestParam(required = false) final String supportType,
            @Parameter(name = "sorting", description = "최신순, 로드맵에 저장 많은 순") @RequestParam(required = false) final String sorting,
            @Parameter(name = "search", description = "검색어") @RequestParam(required = false) final String search
    ) {
        return ResponseEntity.ok(announcementService.findAnnouncements(userPrincipal, pageable, postTarget, region, supportType, sorting, search));
    }

    @Operation(summary = "교내 학사 제도 검색", description = "교내 학사 제도 검색")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "교내 학사 제도 검색 성공", content = {@Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = SystemRes.class)))}),
            @ApiResponse(responseCode = "400", description = "교내 학사 제도 검색 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @GetMapping("/list/system")
    public ResponseEntity<List<SystemRes>> findSystems(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal
    ) {
        return ResponseEntity.ok(announcementService.findSystems(userPrincipal));
    }

    @Operation(summary = "공고 상세정보 조회", description = "공고 상세정보 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "공고 상세정보 조회 성공", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = AnnouncementDetailRes.class))}),
            @ApiResponse(responseCode = "400", description = "공고 상세정보 조회 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @GetMapping("/{announcement-id}")
    public ResponseEntity<AnnouncementDetailRes> findAnnouncementDetailById(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @Parameter(name = "announcement-id", required = true) @PathVariable(name = "announcement-id") final Long announcementId
    ) {
        return ResponseEntity.ok(announcementService.findAnnouncementDetailById(userPrincipal, announcementId));
    }

    @Operation(summary = "공고 랜덤 3개 조회", description = "공고 랜덤 3개 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "공고 랜덤 3개 조회 성공", content = {@Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = AnnouncementRes.class)))}),
            @ApiResponse(responseCode = "400", description = "공고 랜덤 3개 조회 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @GetMapping("/random")
    public ResponseEntity<List<AnnouncementRes>> findThreeRandomAnnouncement(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal
    ) {
        return ResponseEntity.ok(announcementService.findThreeRandomAnnouncement(userPrincipal));
    }

    @Operation(summary = "공고 파일 업로드", description = "공고 파일 업로드")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "공고 파일 업로드 성공"),
            @ApiResponse(responseCode = "400", description = "공고 파일 업로드 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @PostMapping("/upload")
    public ResponseEntity<Void> uploadAnnouncementsFile(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal
    ) {
        announcementService.uploadAnnouncementsFile(userPrincipal);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "맞춤 공고 조회", description = "맞춤 공고 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "맞춤 공고 조회 성공", content = {@Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = CustomAnnouncementRes.class)))}),
            @ApiResponse(responseCode = "400", description = "맞춤 공고 조회 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @GetMapping("/custom")
    public ResponseEntity<?> findCustomAnnouncement(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal
    ) {
        return ResponseEntity.ok(announcementService.findCustomAnnouncement(userPrincipal));
    }

}
