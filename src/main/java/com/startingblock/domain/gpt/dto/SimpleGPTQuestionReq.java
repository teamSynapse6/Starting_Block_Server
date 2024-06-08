package com.startingblock.domain.gpt.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;

@Getter
@Builder
public class SimpleGPTQuestionReq implements Serializable {

    @JsonProperty("qid")
    private Long questionId;

    @JsonProperty("content")
    private String content;
}
