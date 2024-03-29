package com.startingblock.domain.heart.presentation;

import com.startingblock.domain.heart.application.HeartService;
import com.startingblock.domain.heart.dto.HeartRequestDto;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Heart", description = "Heart API")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/heart")
public class HeartController {

    private final HeartService heartService;

    @Operation(summary = "하트 누르기", description = "하트 누르기")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "하트 누르기 성공"),
            @ApiResponse(responseCode = "400", description = "하트 누르기 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @PostMapping(value = "/send")
    public ResponseEntity<?> send(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @Parameter(description = "Schemas의 HeartRequest를 참고해주세요.", required = true) @Valid @RequestBody final HeartRequestDto.HeartRequest dto
    ) {
        heartService.send(userPrincipal, dto);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "하트 취소", description = "하트 취소")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "하트 취소 성공"),
            @ApiResponse(responseCode = "400", description = "하트 취소 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @DeleteMapping(value = "/cancel/{heart-id}")
    public ResponseEntity<?> cancel(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @Parameter(description = "하트의 ID입니다.", required = true) @Valid @PathVariable(value = "heart-id") final Long heartId
    ) {
        heartService.cancel(userPrincipal, heartId);
        return ResponseEntity.noContent().build();
    }
}
