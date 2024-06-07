package com.startingblock.domain.gpt.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;

@Getter
@Builder
@ToString
public class SimpleDuplicateReq implements Serializable {

    @JsonProperty("qid")
    private Long qid;

    @JsonProperty("content")
    private String content;
}
