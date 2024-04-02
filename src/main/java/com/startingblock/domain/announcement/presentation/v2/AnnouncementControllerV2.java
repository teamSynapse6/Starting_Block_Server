package com.startingblock.domain.announcement.presentation.v2;

import com.startingblock.domain.announcement.application.AnnouncementService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Announcements V2 API", description = "Announcements V2 API")
@RequestMapping("/api/v2/announcements")
@RestController
@RequiredArgsConstructor
public class AnnouncementControllerV2 {

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
        announcementService.refreshAnnouncementsV2(userPrincipal);
        return ResponseEntity.noContent().build();
    }

}
