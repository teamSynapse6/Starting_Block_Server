package com.startingblock.global.presentation;

import com.startingblock.global.config.security.token.CurrentUser;
import com.startingblock.global.config.security.token.UserPrincipal;
import com.startingblock.global.error.DefaultAuthenticationException;
import com.startingblock.global.infrastructure.model.ModelStorageService;
import com.startingblock.global.infrastructure.model.ModelStorageService.ModelChunkUploadRes;
import com.startingblock.global.infrastructure.model.ModelStorageService.ModelDownload;
import com.startingblock.global.infrastructure.model.ModelStorageService.ModelDownloadInfo;
import com.startingblock.global.infrastructure.model.ModelStorageService.ModelInfoRes;
import com.startingblock.global.infrastructure.model.ModelStorageService.ModelUploadCompleteRes;
import com.startingblock.global.payload.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ModelController {

    private final ModelStorageService modelStorageService;

    @Operation(summary = "온디바이스 모델 chunk 업로드")
    @PostMapping(value = "/model/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ModelChunkUploadRes> upload(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @RequestParam("model_name") final String modelName,
            @RequestParam(value = "upload_id", required = false) final String uploadId,
            @RequestParam("chunk_index") final int chunkIndex,
            @RequestParam("total_chunks") final int totalChunks,
            @RequestParam("file") final MultipartFile file
    ) {
        requireUser(userPrincipal);
        return ResponseEntity.ok(modelStorageService.uploadChunk(modelName, uploadId, chunkIndex, totalChunks, file));
    }

    @Operation(summary = "온디바이스 모델 chunk 업로드 완료")
    @PostMapping(value = "/model/upload/complete", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ModelUploadCompleteRes> completeUpload(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @RequestBody final ModelUploadCompleteReq request
    ) {
        requireUser(userPrincipal);
        return ResponseEntity.ok(modelStorageService.completeUpload(
                request.model_name(),
                request.upload_id(),
                request.total_chunks()
        ));
    }

    @Operation(summary = "다운로드 가능한 온디바이스 모델 목록")
    @GetMapping(value = "/model/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ModelInfoRes>> list(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal
    ) {
        requireUser(userPrincipal);
        return ResponseEntity.ok(modelStorageService.listModels());
    }

    @Operation(summary = "온디바이스 모델 chunk 다운로드")
    @GetMapping(value = "/model/download/{model_name}/{chunk_num}")
    public ResponseEntity<StreamingResponseBody> downloadChunk(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @PathVariable("model_name") final String modelName,
            @PathVariable("chunk_num") final int chunkNum
    ) {
        UserPrincipal currentUser = requireUser(userPrincipal);
        ModelDownload download = modelStorageService.downloadChunk(modelName, chunkNum, currentUser.getId());
        StreamingResponseBody body = outputStream -> {
            try (InputStream inputStream = download.stream()) {
                inputStream.transferTo(outputStream);
            }
        };

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentLength(download.size())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.modelName())
                        .build()
                        .toString())
                .body(body);
    }

    @Operation(summary = "온디바이스 모델 chunk 다운로드 정보")
    @RequestMapping(value = "/model/download/{model_name}/{chunk_num}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> downloadChunkHead(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @PathVariable("model_name") final String modelName,
            @PathVariable("chunk_num") final int chunkNum
    ) {
        requireUser(userPrincipal);
        ModelDownloadInfo download = modelStorageService.downloadChunkInfo(modelName, chunkNum);
        return downloadHeaders(download).build();
    }

    public record ModelUploadCompleteReq(String model_name, String upload_id, int total_chunks) {
    }

    private ResponseEntity.BodyBuilder downloadHeaders(final ModelDownloadInfo download) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentLength(download.size())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.modelName())
                        .build()
                        .toString());
    }

    private UserPrincipal requireUser(final UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            throw new DefaultAuthenticationException(ErrorCode.INVALID_AUTHENTICATION);
        }
        return userPrincipal;
    }
}
