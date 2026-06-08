package com.startingblock.global.infrastructure.feign.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PdfUploadRes {

    private List<Long> failed_items;
    private List<Long> indexing_failed_items;
    private List<Long> indexing_queued_items;
    private String status;
    private List<Long> success_items;

}
