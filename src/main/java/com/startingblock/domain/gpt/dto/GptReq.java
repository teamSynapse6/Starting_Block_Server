package com.startingblock.domain.gpt.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
public class GptReq implements Serializable {

    private String model;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private Double temperature;

    private Boolean stream;

    private List<GptMessage> messages;

    @JsonProperty("top_p")
    private Double topP;

    @Builder
    public GptReq(String model, Integer maxTokens, Double temperature,
                  Boolean stream, List<GptMessage> messages) {
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.stream = stream;
        this.messages = messages;
    }
}
