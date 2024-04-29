package com.startingblock.domain.announcement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class PdfUploadReq {

    private Long id;
    private String url;
    private String format;

}
