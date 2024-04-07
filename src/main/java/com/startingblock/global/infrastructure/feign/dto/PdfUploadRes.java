package com.startingblock.global.infrastructure.feign.dto;

import lombok.Data;

import java.util.List;

@Data
public class PdfUploadRes {

    private List<Long> failed_items;
    private String status;
    private List<Long> success_items;

}
