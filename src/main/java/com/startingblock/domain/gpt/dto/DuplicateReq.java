package com.startingblock.domain.gpt.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@Builder
@ToString
public class DuplicateReq {

    @JsonProperty("oldQuestions")
    private List<SimpleDuplicateReq> oldQuestions;

    @JsonProperty("newQuestion")
    private String newQuestion;

    public String toJson() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(this);
    }
}
