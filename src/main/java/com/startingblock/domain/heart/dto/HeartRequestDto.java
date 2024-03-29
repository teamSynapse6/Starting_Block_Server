package com.startingblock.domain.heart.dto;

import com.startingblock.domain.heart.domain.HeartType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

public class HeartRequestDto {

    @Getter
    public static class HeartRequest {

        @Schema(type = "long", example = "1", description = "하트를 누를 질문/답변/답글의 ID입니다.")
        private Long id;

        @Schema(type = "Enum", example = "QUESTION", description = "하트의 타입입니다.")
        private HeartType heartType;
    }
}
