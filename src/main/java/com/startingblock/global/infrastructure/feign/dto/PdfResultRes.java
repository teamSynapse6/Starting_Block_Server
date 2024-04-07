package com.startingblock.global.infrastructure.feign.dto;

import lombok.Data;

import java.util.List;

@Data
public class PdfResultRes {

    private List<String> file_ids;

}
