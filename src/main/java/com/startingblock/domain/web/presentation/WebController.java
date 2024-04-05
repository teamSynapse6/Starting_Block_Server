package com.startingblock.domain.web.presentation;

import com.startingblock.domain.answer.application.AnswerService;
import com.startingblock.domain.answer.dto.AnswerRequestDto;
import com.startingblock.domain.question.application.QuestionFindService;
import com.startingblock.domain.question.dto.QuestionResponseDto;
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

@Tag(name = "Web", description = "Web API")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/web")
public class WebController {

    private final AnswerService answerService;
    private final QuestionFindService questionFindService;

    // TODO: 웹 공고별 질문 조회 /api/v1/web/question
    @Operation(summary = "웹 공고별 질문 조회", description = "웹 공고별 질문 리스트 조회하기 API입니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "웹 공고별 질문 조회 성공", content = {@Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = QuestionResponseDto.QuestionListResponseForWeb.class)))}),
            @ApiResponse(responseCode = "400", description = "웹 공고별 질문 조회 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @GetMapping(value = "/question/{announcement-id}")
    public ResponseEntity<?> findByAnnouncementForWeb(
            @Parameter(description = "공고의 ID입니다.", required = true) @Valid @PathVariable(name = "announcement-id") final Long announcementId
    ) {
        return ResponseEntity.ok(questionFindService.findByAnnouncementForWeb(announcementId));
    }


    // TODO: 답변하기 (담당자)  /api/v1/web/answer
    @Operation(summary = "답변하기(담당자)", description = "웹에서 사용하는 답변하기(담당자) API입니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "답변 성공"),
            @ApiResponse(responseCode = "400", description = "답변 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @PostMapping(value = "/answer")
    public ResponseEntity<?> sendContactAnswer(
            @Parameter(description = "Schemas의 AnswerRequest를 참고해주세요.", required = true) @Valid @RequestBody final AnswerRequestDto.AnswerRequest dto
    ) {
        answerService.sendContactAnswer(dto);
        return ResponseEntity.noContent().build();
    }

    // TODO: 모든 답변 완료    /api/v1/web/answer/all
    @Operation(summary = "모든 답변 완료", description = "웹에서 사용하는 모든 답변 완료 API입니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "모든 답변 완료 성공"),
            @ApiResponse(responseCode = "400", description = "모든 답변 완료 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))})
    })
    @PostMapping(value = "/answer/all")
    public ResponseEntity<?> sendContactAnswerAll(
            @Parameter(description = "Schemas의 AnswerRequest를 참고해주세요.", required = true) @Valid @RequestBody final AnswerRequestDto.AnswerListRequest dto
    ) {
        answerService.sendContactAnswerAll(dto);
        return ResponseEntity.noContent().build();
    }
}
