package com.startingblock.domain.reply.presentation;

import com.startingblock.domain.reply.application.ReplyService;
import com.startingblock.domain.reply.dto.ReplyRequestDto;
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

@Tag(name = "Reply", description = "Reply API")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/reply")
public class ReplyController {

    private final ReplyService replyService;

    @Operation(summary = "답글 쓰기", description = "답글 쓰기")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "답글 쓰기 성공"),
            @ApiResponse(responseCode = "400", description = "답글 쓰기 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @PostMapping(value = "/send")
    public ResponseEntity<?> send(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @Parameter(description = "Schemas의 ReplyRequest를 참고해주세요.", required = true) @Valid @RequestBody final ReplyRequestDto.ReplyRequest dto
    ) {
        replyService.send(userPrincipal, dto);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "답글 삭제", description = "답글 삭제")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "답글 삭제 성공"),
            @ApiResponse(responseCode = "400", description = "답글 삭제 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @DeleteMapping(value = "/cancel/{reply-id}")
    public ResponseEntity<?> cancel(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @Parameter(description = "답글의 ID입니다.", required = true) @Valid @PathVariable(value = "reply-id") Long replyId
    ) {
        replyService.cancel(userPrincipal, replyId);
        return ResponseEntity.noContent().build();
    }
}
