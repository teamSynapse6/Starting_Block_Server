package com.startingblock.domain.roadmap.presentation;

import com.startingblock.domain.roadmap.application.RoadmapService;
import com.startingblock.domain.roadmap.dto.RoadmapRegisterReq;
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
import org.springframework.web.bind.annotation.*;

@Tag(name = "RoadMaps API", description = "RoadMaps API")
@RequestMapping("/api/v1/roadmaps")
@RestController
@RequiredArgsConstructor
public class RoadmapController {

    private final RoadmapService roadmapService;

    @Operation(summary = "로드맵 초기 등록", description = "로드맵 초기 등록")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로드맵 초기 등록 성공", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = RoadmapRegisterReq.class))}),
            @ApiResponse(responseCode = "400", description = "로드맵 초기 등록 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @PostMapping
    public ResponseEntity<Void> registerRoadMap(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @Parameter(name = "roadMapRegisterReq", description = "로드맵 초기 등록 정보") @RequestBody final RoadmapRegisterReq roadMapRegisterReq
    ) {
        roadmapService.registerRoadmaps(userPrincipal, roadMapRegisterReq);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "로드맵에 공고 저장", description = "로드맵에 공고 저장")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로드맵에 공고 저장 성공", content = {@Content(mediaType = "application/json")}),
            @ApiResponse(responseCode = "400", description = "로드맵에 공고 저장 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @PostMapping("/{roadmap-id}")
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
    @DeleteMapping("/{roadmap-id}")
    public ResponseEntity<Void> deleteRoadmapAnnouncement(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @Parameter(name = "roadmap-id", description = "로드맵 ID") @PathVariable(name = "roadmap-id") final Long roadmapId,
            @Parameter(name = "announcementId", description = "공고 ID") @RequestParam(name = "announcementId") final Long announcementId
    ) {
        roadmapService.deleteRoadmapAnnouncement(userPrincipal, roadmapId, announcementId);
        return ResponseEntity.noContent().build();
    }

}
