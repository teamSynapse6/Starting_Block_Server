package com.startingblock.domain.mail.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MailRequestDto {
    private String email;
    private String announcement;
    private String link;
}
