package com.startingblock.domain.question.presentation;

import com.startingblock.domain.auth.dto.AuthRes;
import com.startingblock.domain.auth.dto.SignInReq;
import com.startingblock.domain.question.application.QuestionFindService;
import com.startingblock.domain.question.application.QuestionService;
import com.startingblock.domain.question.dto.QuestionRequestDto;
import com.startingblock.domain.question.dto.QuestionResponseDto;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Question", description = "Question API")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/question")
public class QuestionController {

    private final QuestionService questionService;
    private final QuestionFindService questionFindService;

    @Operation(summary = "질문하기", description = "질문하기")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "질문 성공"),
            @ApiResponse(responseCode = "400", description = "질문 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @PostMapping(value = "/ask")
    public ResponseEntity<?> ask(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @Parameter(description = "Schemas의 AskQuestionRequest를 참고해주세요.", required = true) @Valid @RequestBody final QuestionRequestDto.AskQuestionRequest dto
    ) {
        questionService.ask(userPrincipal, dto);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "질문 리스트 조회", description = "질문 리스트 조회하기")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "질문 리스트 조회 성공", content = {@Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = QuestionResponseDto.QuestionListResponse.class)))}),
            @ApiResponse(responseCode = "400", description = "질문 리스트 조회 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @GetMapping(value = "/{announcement-id}")
    public ResponseEntity<?> findByAnnouncement(
            @Parameter(description = "공고의 ID입니다.", required = true) @Valid @PathVariable(name = "announcement-id") final Long announcementId
    ) {
        return ResponseEntity.ok(questionFindService.findByAnnouncement(announcementId));
    }
}
