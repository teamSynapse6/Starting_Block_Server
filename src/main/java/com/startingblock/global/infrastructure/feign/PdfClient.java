package com.startingblock.global.infrastructure.feign;

import com.startingblock.domain.announcement.dto.PdfUploadReq;
import com.startingblock.global.infrastructure.feign.dto.PdfResultRes;
import com.startingblock.global.infrastructure.feign.dto.PdfUploadRes;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "pdf", url = "https://pdfgpt.startingblock.co.kr")
@Component
public interface PdfClient {

    @PostMapping("/announcement/upload")
    PdfUploadRes uploadPdf(final @RequestBody List<PdfUploadReq> pdfUploadReq);

    @GetMapping("/validation")
    PdfResultRes getUploadPdfResult();

}
