package com.startingblock.domain.answer.presentation;

import com.startingblock.domain.answer.application.AnswerService;
import com.startingblock.domain.answer.dto.AnswerRequestDto;
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

@Tag(name = "Answer", description = "Answer API")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/answer")
public class AnswerController {

    private final AnswerService answerService;

    @Operation(summary = "답변하기(사용자)", description = "답변하기(사용자)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "답변 성공"),
            @ApiResponse(responseCode = "400", description = "답변 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @PostMapping(value = "/send")
    public ResponseEntity<?> sendGeneralAnswer(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @Parameter(description = "Schemas의 AnswerRequest를 참고해주세요.", required = true) @Valid @RequestBody final AnswerRequestDto.AnswerRequest dto
    ) {
        answerService.sendGeneralAnswer(userPrincipal, dto);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "답변 삭제", description = "답변 삭제")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "답변 삭제 성공"),
            @ApiResponse(responseCode = "400", description = "답변 삭제 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @DeleteMapping(value = "/cancel/{answer-id}")
    public ResponseEntity<?> cancel(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @Parameter(description = "답변의 ID입니다.", required = true) @Valid @PathVariable(value = "answer-id") Long answerId
    ) {
        answerService.cancel(userPrincipal, answerId);
        return ResponseEntity.noContent().build();
    }
}
