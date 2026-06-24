package com.startingblock.domain.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;

@Getter
@Builder
public class SimpleLlmQuestionReq implements Serializable {

    @JsonProperty("qid")
    private Long questionId;

    @JsonProperty("content")
    private String content;
}
