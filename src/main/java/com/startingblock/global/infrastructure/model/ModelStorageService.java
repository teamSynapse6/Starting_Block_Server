package com.startingblock.global.infrastructure.model;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ModelStorageService {

    private static final String MODEL_PREFIX = "model/";
    private static final String UPLOAD_PREFIX = MODEL_PREFIX + ".uploads/";
    private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final MinioClient minioClient;
    private final ModelDownloadHistoryRepository modelDownloadHistoryRepository;

    @Value("${minio.bucket:startingblock-pdfgpt}")
    private String bucket;

    public ModelChunkUploadRes uploadChunk(
            final String rawModelName,
            final String rawUploadId,
            final int chunkIndex,
            final int totalChunks,
            final MultipartFile file
    ) {
        String modelName = normalizeModelName(rawModelName);
        String uploadId = normalizeUploadId(rawUploadId, modelName);
        validateChunkIndex(chunkIndex, totalChunks);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "업로드할 chunk 파일이 필요합니다.");
        }

        try {
            ensureBucket();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(chunkKey(uploadId, chunkIndex))
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType("application/octet-stream")
                            .build()
            );
            return new ModelChunkUploadRes(modelName, uploadId, chunkIndex, totalChunks, file.getSize());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "모델 chunk 업로드 중 오류가 발생했습니다.", exception);
        }
    }

    public ModelUploadCompleteRes completeUpload(final String rawModelName, final String rawUploadId, final int totalChunks) {
        String modelName = normalizeModelName(rawModelName);
        String uploadId = normalizeUploadId(rawUploadId, modelName);
        if (totalChunks <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "total_chunks는 1 이상이어야 합니다.");
        }

        try {
            ensureBucket();
            List<String> chunkKeys = new ArrayList<>();
            long totalSize = 0;
            for (int index = 0; index < totalChunks; index++) {
                String key = chunkKey(uploadId, index);
                StatObjectResponse stat = minioClient.statObject(
                        StatObjectArgs.builder()
                                .bucket(bucket)
                                .object(key)
                                .build()
                );
                chunkKeys.add(key);
                totalSize += stat.size();
            }

            try (InputStream stream = new MinioSequenceInputStream(minioClient, bucket, chunkKeys)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(modelKey(modelName))
                                .stream(stream, totalSize, -1)
                                .contentType("application/octet-stream")
                                .build()
                );
            }

            for (String key : chunkKeys) {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucket)
                                .object(key)
                                .build()
                );
            }

            return new ModelUploadCompleteRes(modelName, totalSize);
        } catch (ErrorResponseException exception) {
            if ("NoSuchKey".equals(exception.errorResponse().code())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "업로드되지 않은 chunk가 있습니다.", exception);
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "모델 업로드 완료 처리 중 오류가 발생했습니다.", exception);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "모델 업로드 완료 처리 중 오류가 발생했습니다.", exception);
        }
    }

    public List<ModelInfoRes> listModels() {
        try {
            ensureBucket();
            List<ModelInfoRes> models = new ArrayList<>();
            Iterable<io.minio.Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(MODEL_PREFIX)
                            .recursive(true)
                            .build()
            );
            for (io.minio.Result<Item> result : results) {
                Item item = result.get();
                String objectName = item.objectName();
                if (item.isDir() || objectName.startsWith(UPLOAD_PREFIX) || objectName.equals(MODEL_PREFIX)) {
                    continue;
                }
                String modelName = objectName.substring(MODEL_PREFIX.length());
                if (modelName.isBlank() || modelName.contains("/")) {
                    continue;
                }
                models.add(new ModelInfoRes(modelName, item.size()));
            }
            return models;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "모델 목록 조회 중 오류가 발생했습니다.", exception);
        }
    }

    public ModelDownload download(final String rawModelName, final Long userId) {
        String modelName = normalizeModelName(rawModelName);
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(modelKey(modelName))
                            .build()
            );
            GetObjectResponse stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(modelKey(modelName))
                            .build()
            );
            modelDownloadHistoryRepository.save(userId, modelName, stat.size());
            return new ModelDownload(modelName, stat.size(), stream);
        } catch (ErrorResponseException exception) {
            if ("NoSuchKey".equals(exception.errorResponse().code())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "모델 파일을 찾을 수 없습니다.", exception);
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "모델 다운로드 준비 중 오류가 발생했습니다.", exception);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "모델 다운로드 준비 중 오류가 발생했습니다.", exception);
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                        .bucket(bucket)
                        .build()
        );
        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(bucket)
                            .build()
            );
        }
    }

    private String normalizeModelName(final String value) {
        String modelName = value == null ? "" : value.trim();
        if (modelName.isBlank() || modelName.length() > 255 || !SAFE_NAME.matcher(modelName).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "model_name은 영문, 숫자, 점, 밑줄, 하이픈만 사용할 수 있습니다.");
        }
        String lower = modelName.toLowerCase(Locale.ROOT);
        if (lower.equals(".") || lower.equals("..") || lower.startsWith(".uploads")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사용할 수 없는 model_name입니다.");
        }
        return modelName;
    }

    private String normalizeUploadId(final String value, final String modelName) {
        String uploadId = value == null || value.isBlank() ? modelName : value.trim();
        if (uploadId.equalsIgnoreCase("new")) {
            uploadId = UUID.randomUUID().toString();
        }
        if (uploadId.length() > 255 || !SAFE_NAME.matcher(uploadId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "upload_id는 영문, 숫자, 점, 밑줄, 하이픈만 사용할 수 있습니다.");
        }
        return uploadId;
    }

    private void validateChunkIndex(final int chunkIndex, final int totalChunks) {
        if (totalChunks <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "total_chunks는 1 이상이어야 합니다.");
        }
        if (chunkIndex < 0 || chunkIndex >= totalChunks) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chunk_index 범위가 올바르지 않습니다.");
        }
    }

    private String chunkKey(final String uploadId, final int chunkIndex) {
        return UPLOAD_PREFIX + uploadId + "/" + String.format("%08d.part", chunkIndex);
    }

    private String modelKey(final String modelName) {
        return MODEL_PREFIX + modelName;
    }

    public record ModelChunkUploadRes(String model_name, String upload_id, int chunk_index, int total_chunks, long size) {
    }

    public record ModelUploadCompleteRes(String model_name, long size) {
    }

    public record ModelInfoRes(String model_name, long size) {
    }

    public record ModelDownload(String modelName, long size, InputStream stream) {
    }

    private static class MinioSequenceInputStream extends InputStream {
        private final MinioClient minioClient;
        private final String bucket;
        private final List<String> objectNames;
        private int index = 0;
        private InputStream current;

        private MinioSequenceInputStream(final MinioClient minioClient, final String bucket, final List<String> objectNames) {
            this.minioClient = minioClient;
            this.bucket = bucket;
            this.objectNames = objectNames;
        }

        @Override
        public int read() throws IOException {
            byte[] buffer = new byte[1];
            int read = read(buffer, 0, 1);
            return read == -1 ? -1 : buffer[0] & 0xff;
        }

        @Override
        public int read(final byte[] buffer, final int offset, final int length) throws IOException {
            while (true) {
                if (current == null && !openNext()) {
                    return -1;
                }
                int read = current.read(buffer, offset, length);
                if (read != -1) {
                    return read;
                }
                closeCurrent();
            }
        }

        @Override
        public void close() throws IOException {
            closeCurrent();
        }

        private boolean openNext() throws IOException {
            if (index >= objectNames.size()) {
                return false;
            }
            try {
                current = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectNames.get(index))
                                .build()
                );
                index += 1;
                return true;
            } catch (Exception exception) {
                throw new IOException(exception);
            }
        }

        private void closeCurrent() throws IOException {
            if (current != null) {
                current.close();
                current = null;
            }
        }
    }
}
