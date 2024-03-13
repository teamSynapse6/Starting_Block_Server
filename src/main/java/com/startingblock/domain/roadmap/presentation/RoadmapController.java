package com.startingblock.domain.roadmap.presentation;

import com.startingblock.domain.roadmap.application.RoadmapService;
import com.startingblock.domain.roadmap.dto.RoadmapAddReq;
import com.startingblock.domain.roadmap.dto.RoadmapDetailRes;
import com.startingblock.domain.roadmap.dto.RoadmapRegisterReq;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Roadmaps API", description = "Roadmaps API")
@RequestMapping("/api/v1/roadmaps")
@RestController
@RequiredArgsConstructor
public class RoadmapController {

    private final RoadmapService roadmapService;

    @Operation(summary = "로드맵 리스트 조회", description = "로드맵 리스트 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로드맵 리스트 조회 성공", content = {@Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = RoadmapDetailRes.class)))}),
            @ApiResponse(responseCode = "400", description = "로드맵 리스트 조회 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @GetMapping
    public ResponseEntity<List<RoadmapDetailRes>> findRoadmaps(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal
    ) {
        return ResponseEntity.ok(roadmapService.findRoadmaps(userPrincipal));
    }

    @Operation(summary = "로드맵 초기 등록", description = "로드맵 초기 등록")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로드맵 초기 등록 성공", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = RoadmapRegisterReq.class))}),
            @ApiResponse(responseCode = "400", description = "로드맵 초기 등록 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @PostMapping
    public ResponseEntity<List<RoadmapDetailRes>> registerRoadMap(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @Parameter(name = "roadMapRegisterReq", description = "로드맵 초기 등록 정보") @RequestBody final RoadmapRegisterReq roadMapRegisterReq
    ) {
        return ResponseEntity.ok(roadmapService.registerRoadmaps(userPrincipal, roadMapRegisterReq));
    }

    @Operation(summary = "로드맵 단계 삭제", description = "로드맵 단계 삭제")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로드맵 단계 삭제 성공", content = {@Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = RoadmapDetailRes.class)))}),
            @ApiResponse(responseCode = "400", description = "로드맵 단계 삭제 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @DeleteMapping("/{roadmap-id}")
    public ResponseEntity<List<RoadmapDetailRes>> deleteRoadmap(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @Parameter(name = "roadmapId", description = "로드맵 ID") @PathVariable(name = "roadmap-id") final Long roadmapId
    ) {
        return ResponseEntity.ok(roadmapService.deleteRoadmap(userPrincipal, roadmapId));
    }

    @Operation(summary = "로드맵 단계 추가", description = "로드맵 단계 추가")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로드맵 단계 추가 성공", content = {@Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = RoadmapDetailRes.class)))}),
            @ApiResponse(responseCode = "400", description = "로드맵 단계 추가 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @PostMapping("/add")
    public ResponseEntity<List<RoadmapDetailRes>> addRoadmap(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @Parameter(description = "RoadmapAddReq Schema 확인") @RequestBody final RoadmapAddReq roadmapAddReq
            ) {
        return ResponseEntity.ok(roadmapService.addRoadmap(userPrincipal, roadmapAddReq));
    }

    @Operation(summary = "로드맵에 공고 저장", description = "로드맵에 공고 저장")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로드맵에 공고 저장 성공", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "400", description = "로드맵에 공고 저장 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @PostMapping("/{roadmap-id}/announcement")
    public ResponseEntity<Void> addRoadmapAnnouncement(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @Parameter(name = "roadmap-id", description = "로드맵 ID") @PathVariable(name = "roadmap-id") final Long roadmapId,
            @Parameter(name = "announcementId", description = "공고 ID") @RequestParam(name = "announcementId") final Long announcementId
    ) {
        roadmapService.addRoadmapAnnouncement(userPrincipal, roadmapId, announcementId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "로드맵에 공고 삭제", description = "로드맵에 공고 삭제")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로드맵에 공고 삭제 성공", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "400", description = "로드맵에 공고 삭제 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @DeleteMapping("/{roadmap-id}/announcement")
    public ResponseEntity<Void> deleteRoadmapAnnouncement(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @Parameter(name = "roadmap-id", description = "로드맵 ID") @PathVariable(name = "roadmap-id") final Long roadmapId,
            @Parameter(name = "announcementId", description = "공고 ID") @RequestParam(name = "announcementId") final Long announcementId
    ) {
        roadmapService.deleteRoadmapAnnouncement(userPrincipal, roadmapId, announcementId);
        return ResponseEntity.noContent().build();
    }

}
