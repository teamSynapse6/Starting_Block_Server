package com.startingblock.domain.gpt.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class GroupingQuestionRes {

    private List<Long> questionId;
    private String content;

}
