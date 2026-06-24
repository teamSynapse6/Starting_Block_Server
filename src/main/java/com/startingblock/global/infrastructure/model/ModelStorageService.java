package com.startingblock.global.infrastructure.model;

import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ModelStorageService {

    private static final String MODEL_PREFIX = "model/";
    private static final String UPLOAD_PREFIX = MODEL_PREFIX + ".uploads/";
    private static final String LEGACY_CHUNK_PREFIX = MODEL_PREFIX + ".chunks/";
    private static final String LEGACY_MODEL_PREFIX = MODEL_PREFIX + ".legacy/";
    private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final MinioClient minioClient;
    private final ModelDownloadHistoryRepository modelDownloadHistoryRepository;
    private final LlmModelConfigRepository llmModelConfigRepository;

    @Value("${minio.bucket:startingblock-pdfllm}")
    private String bucket;

    @Value("${model.download-chunk-size-bytes:104857600}")
    private long downloadChunkSizeBytes;

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

            removeObjectIfExists(modelKey(modelName));
            removeObjectIfExists(legacyModelKey(modelName));
            removeObjectsByPrefix(legacyModelChunkPrefix(modelName));

            ChunkSummary downloadChunkSummary;
            try (InputStream stream = new MinioSequenceInputStream(minioClient, bucket, chunkKeys)) {
                downloadChunkSummary = writeDownloadChunks(modelName, totalSize, stream);
            }

            for (String key : chunkKeys) {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucket)
                                .object(key)
                                .build()
                );
            }

            ensureModelConfig(modelName);
            return new ModelUploadCompleteRes(modelName, totalSize, downloadChunkSummary.count());
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
            Map<String, Long> legacyFullObjects = new LinkedHashMap<>();
            Map<String, ChunkSummary> chunkSummaries = new LinkedHashMap<>();
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
                if (item.isDir()
                        || objectName.startsWith(UPLOAD_PREFIX)
                        || objectName.startsWith(LEGACY_CHUNK_PREFIX)
                        || objectName.startsWith(LEGACY_MODEL_PREFIX)
                        || objectName.equals(MODEL_PREFIX)) {
                    continue;
                }
                String relativeName = objectName.substring(MODEL_PREFIX.length());
                if (relativeName.isBlank()) {
                    continue;
                }
                int separatorIndex = relativeName.indexOf('/');
                if (separatorIndex < 0) {
                    if (isSafeModelName(relativeName)) {
                        legacyFullObjects.put(relativeName, item.size());
                    }
                    continue;
                }

                String modelName = relativeName.substring(0, separatorIndex);
                String chunkName = relativeName.substring(separatorIndex + 1);
                if (!isSafeModelName(modelName) || !isChunkObjectName(chunkName)) {
                    continue;
                }
                ChunkSummary previous = chunkSummaries.getOrDefault(modelName, new ChunkSummary(0, 0));
                chunkSummaries.put(modelName, new ChunkSummary(previous.count() + 1, previous.size() + item.size()));
            }

            List<ModelInfoRes> models = new ArrayList<>();
            for (Map.Entry<String, Long> legacyFullObject : legacyFullObjects.entrySet()) {
                String modelName = legacyFullObject.getKey();
                long fullSize = legacyFullObject.getValue();
                ChunkSummary chunkSummary = chunkSummaries.get(modelName);
                if (chunkSummary == null || chunkSummary.size() != fullSize) {
                    chunkSummary = rebuildDownloadChunksFromModel(modelName, fullSize);
                    chunkSummaries.put(modelName, chunkSummary);
                } else {
                    prepareLegacyModelSource(modelName);
                }
            }

            for (Map.Entry<String, ChunkSummary> chunkEntry : chunkSummaries.entrySet()) {
                ChunkSummary chunkSummary = chunkEntry.getValue();
                if (chunkSummary.count() > 0) {
                    ModelNameParts nameParts = splitStorageModelName(chunkEntry.getKey());
                    models.add(new ModelInfoRes(
                            nameParts.modelName(),
                            nameParts.format(),
                            chunkSummary.size(),
                            chunkSummary.count()
                    ));
                }
            }
            return models;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "모델 목록 조회 중 오류가 발생했습니다.", exception);
        }
    }

    public ModelDownload download(final String rawModelName, final Long userId) {
        String modelName = normalizeModelName(rawModelName);
        try {
            ChunkSummary chunkSummary = ensureModelChunks(modelName);
            List<String> chunkKeys = modelChunkKeys(modelName, chunkSummary.count());
            InputStream stream = new MinioSequenceInputStream(minioClient, bucket, chunkKeys);
            modelDownloadHistoryRepository.save(userId, modelName, chunkSummary.size());
            return new ModelDownload(modelName, chunkSummary.size(), stream);
        } catch (ErrorResponseException exception) {
            if ("NoSuchKey".equals(exception.errorResponse().code())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "모델 파일을 찾을 수 없습니다.", exception);
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "모델 다운로드 준비 중 오류가 발생했습니다.", exception);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "모델 다운로드 준비 중 오류가 발생했습니다.", exception);
        }
    }

    public ModelDownload downloadChunk(final String rawModelName, final int chunkNum, final Long userId) {
        try {
            String modelName = resolveStoredModelName(rawModelName);
            int chunkIndex = toChunkIndex(chunkNum);
            ModelObjectInfo objectInfo = modelChunkObjectInfo(modelName, chunkIndex);

            GetObjectResponse stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectInfo.objectName())
                            .build()
            );
            modelDownloadHistoryRepository.save(userId, modelName, objectInfo.size());
            return new ModelDownload(downloadChunkFileName(modelName, chunkNum), objectInfo.size(), stream);
        } catch (ErrorResponseException exception) {
            if ("NoSuchKey".equals(exception.errorResponse().code())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "모델 chunk 파일을 찾을 수 없습니다.", exception);
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "모델 chunk 다운로드 준비 중 오류가 발생했습니다.", exception);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "모델 chunk 다운로드 준비 중 오류가 발생했습니다.", exception);
        }
    }

    public ModelDownloadInfo downloadInfo(final String rawModelName) {
        String modelName = normalizeModelName(rawModelName);
        try {
            ChunkSummary chunkSummary = ensureModelChunks(modelName);
            return new ModelDownloadInfo(modelName, chunkSummary.size());
        } catch (ErrorResponseException exception) {
            if ("NoSuchKey".equals(exception.errorResponse().code())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "모델 파일을 찾을 수 없습니다.", exception);
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "모델 다운로드 정보 조회 중 오류가 발생했습니다.", exception);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "모델 다운로드 정보 조회 중 오류가 발생했습니다.", exception);
        }
    }

    public ModelDownloadInfo downloadChunkInfo(final String rawModelName, final int chunkNum) {
        try {
            String modelName = resolveStoredModelName(rawModelName);
            int chunkIndex = toChunkIndex(chunkNum);
            ModelObjectInfo objectInfo = modelChunkObjectInfo(modelName, chunkIndex);
            return new ModelDownloadInfo(downloadChunkFileName(modelName, chunkNum), objectInfo.size());
        } catch (ErrorResponseException exception) {
            if ("NoSuchKey".equals(exception.errorResponse().code())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "모델 chunk 파일을 찾을 수 없습니다.", exception);
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "모델 chunk 다운로드 정보 조회 중 오류가 발생했습니다.", exception);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "모델 chunk 다운로드 정보 조회 중 오류가 발생했습니다.", exception);
        }
    }

    public List<ModelConfigRes> listModelConfigs() {
        return llmModelConfigRepository.findAll(Sort.by(Sort.Direction.ASC, "modelName"))
                .stream()
                .map(config -> new ModelConfigRes(
                        config.getModelName(),
                        config.getUpdatedAt(),
                        config.getTemperature(),
                        config.getTopP(),
                        config.getTopK(),
                        config.getMaxOutputTokens(),
                        config.getSystemInstruction()
                ))
                .toList();
    }

    @Transactional
    public ModelConfigRes updateModelConfig(final ModelConfigUpdateReq request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "모델 설정 요청 body가 필요합니다.");
        }
        String modelName = normalizeModelName(request.model_name());
        LlmModelConfig config = llmModelConfigRepository.findByModelName(modelName)
                .orElseGet(() -> {
                    String resolvedModelName = resolveExistingModelName(modelName);
                    return llmModelConfigRepository.findByModelName(resolvedModelName)
                            .orElseGet(() -> new LlmModelConfig(resolvedModelName));
                });

        config.update(
                request.temperature(),
                request.top_p(),
                request.top_k(),
                request.max_output_tokens(),
                request.systemInstruction()
        );
        LlmModelConfig saved = llmModelConfigRepository.saveAndFlush(config);
        return new ModelConfigRes(
                saved.getModelName(),
                saved.getUpdatedAt(),
                saved.getTemperature(),
                saved.getTopP(),
                saved.getTopK(),
                saved.getMaxOutputTokens(),
                saved.getSystemInstruction()
        );
    }

    @Transactional
    public void deleteModel(final String rawModelName) {
        String modelName = normalizeModelName(rawModelName);
        String storageModelName = modelName;

        try {
            ensureBucket();
            if (!storedModelExists(modelName)) {
                List<String> matches = findStoredModelNames(modelName);
                if (matches.size() > 1) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "동일한 model_name의 여러 format이 존재합니다.");
                }
                if (matches.size() == 1) {
                    storageModelName = matches.get(0);
                }
            }

            removeObjectIfExists(modelKey(storageModelName));
            removeObjectIfExists(legacyModelKey(storageModelName));
            removeObjectsByPrefix(modelChunkPrefix(storageModelName));
            removeObjectsByPrefix(legacyModelChunkPrefix(storageModelName));
            removeObjectsByPrefix(UPLOAD_PREFIX + storageModelName + "/");

            llmModelConfigRepository.deleteByModelName(storageModelName);
            if (!storageModelName.equals(modelName)) {
                llmModelConfigRepository.deleteByModelName(modelName);
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "모델 삭제 중 오류가 발생했습니다.", exception);
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
        if (lower.equals(".") || lower.equals("..") || lower.startsWith(".uploads") || lower.startsWith(".chunks")) {
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

    private String modelChunkPrefix(final String modelName) {
        return MODEL_PREFIX + modelName + "/";
    }

    private String legacyModelChunkPrefix(final String modelName) {
        return LEGACY_CHUNK_PREFIX + modelName + "/";
    }

    private String modelChunkKey(final String modelName, final int chunkIndex) {
        return modelChunkPrefix(modelName) + String.format("%08d.part", chunkIndex);
    }

    private String modelKey(final String modelName) {
        return MODEL_PREFIX + modelName;
    }

    private String legacyModelKey(final String modelName) {
        return LEGACY_MODEL_PREFIX + modelName;
    }

    private ModelObjectInfo modelObjectInfo(final String modelName) throws Exception {
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(modelKey(modelName))
                            .build()
            );
            return new ModelObjectInfo(modelKey(modelName), stat.size());
        } catch (ErrorResponseException exception) {
            if (!"NoSuchKey".equals(exception.errorResponse().code())) {
                throw exception;
            }
        }

        StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(bucket)
                        .object(legacyModelKey(modelName))
                        .build()
        );
        return new ModelObjectInfo(legacyModelKey(modelName), stat.size());
    }

    private LlmModelConfig ensureModelConfig(final String modelName) {
        return llmModelConfigRepository.findByModelName(modelName)
                .orElseGet(() -> llmModelConfigRepository.save(new LlmModelConfig(modelName)));
    }

    private String resolveExistingModelName(final String modelName) {
        try {
            return resolveStoredModelName(modelName);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "모델 설정 대상 조회 중 오류가 발생했습니다.", exception);
        }
    }

    private ModelObjectInfo modelChunkObjectInfo(final String modelName, final int chunkNum) throws Exception {
        ensureModelChunks(modelName);
        String objectName = modelChunkKey(modelName, chunkNum);
        StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build()
        );
        return new ModelObjectInfo(objectName, stat.size());
    }

    private int toChunkIndex(final int chunkNum) {
        if (chunkNum <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chunk_num은 1 이상이어야 합니다.");
        }
        return chunkNum - 1;
    }

    private String resolveStoredModelName(final String rawModelName) throws Exception {
        String requestedModelName = normalizeModelName(rawModelName);
        if (storedModelExists(requestedModelName)) {
            return requestedModelName;
        }

        List<String> matches = findStoredModelNames(requestedModelName);
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "모델 파일을 찾을 수 없습니다.");
        }
        if (matches.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "동일한 model_name의 여러 format이 존재합니다.");
        }
        return matches.get(0);
    }

    private boolean storedModelExists(final String modelName) throws Exception {
        if (chunkSummary(modelName).count() > 0) {
            return true;
        }
        return objectExists(modelKey(modelName)) || objectExists(legacyModelKey(modelName));
    }

    private boolean objectExists(final String objectName) throws Exception {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build()
            );
            return true;
        } catch (ErrorResponseException exception) {
            if ("NoSuchKey".equals(exception.errorResponse().code())) {
                return false;
            }
            throw exception;
        }
    }

    private List<String> findStoredModelNames(final String publicModelName) throws Exception {
        List<String> matches = new ArrayList<>();
        Iterable<io.minio.Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucket)
                        .prefix(MODEL_PREFIX)
                        .recursive(true)
                        .build()
        );
        for (io.minio.Result<Item> result : results) {
            Item item = result.get();
            if (item.isDir()) {
                continue;
            }
            String objectName = item.objectName();
            if (objectName.startsWith(UPLOAD_PREFIX) || objectName.startsWith(LEGACY_CHUNK_PREFIX)) {
                continue;
            }

            String storageModelName = null;
            if (objectName.startsWith(LEGACY_MODEL_PREFIX)) {
                storageModelName = objectName.substring(LEGACY_MODEL_PREFIX.length());
            } else if (objectName.startsWith(MODEL_PREFIX)) {
                String relativeName = objectName.substring(MODEL_PREFIX.length());
                int separatorIndex = relativeName.indexOf('/');
                storageModelName = separatorIndex < 0 ? relativeName : relativeName.substring(0, separatorIndex);
            }
            if (!isSafeModelName(storageModelName)) {
                continue;
            }

            ModelNameParts nameParts = splitStorageModelName(storageModelName);
            if (nameParts.modelName().equals(publicModelName) && !matches.contains(storageModelName)) {
                matches.add(storageModelName);
            }
        }
        return matches;
    }

    private String downloadChunkFileName(final String storageModelName, final int chunkNum) {
        return storageModelName + "." + String.format("%08d.part", chunkNum);
    }

    private ModelNameParts splitStorageModelName(final String storageModelName) {
        int separatorIndex = storageModelName.lastIndexOf('.');
        if (separatorIndex <= 0 || separatorIndex == storageModelName.length() - 1) {
            return new ModelNameParts(storageModelName, "");
        }
        return new ModelNameParts(
                storageModelName.substring(0, separatorIndex),
                storageModelName.substring(separatorIndex + 1)
        );
    }

    private ChunkSummary ensureModelChunks(final String modelName) throws Exception {
        ChunkSummary chunkSummary = chunkSummary(modelName);
        if (chunkSummary.count() > 0) {
            return chunkSummary;
        }
        ModelObjectInfo legacyObjectInfo = modelObjectInfo(modelName);
        return rebuildDownloadChunksFromModel(modelName, legacyObjectInfo.size());
    }

    private ChunkSummary chunkSummary(final String modelName) throws Exception {
        int count = 0;
        long size = 0;
        Iterable<io.minio.Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucket)
                        .prefix(modelChunkPrefix(modelName))
                        .recursive(true)
                        .build()
        );
        for (io.minio.Result<Item> result : results) {
            Item item = result.get();
            if (!item.isDir() && isChunkObjectName(item.objectName().substring(modelChunkPrefix(modelName).length()))) {
                count += 1;
                size += item.size();
            }
        }
        return new ChunkSummary(count, size);
    }

    private List<String> modelChunkKeys(final String modelName, final int chunkCount) {
        List<String> chunkKeys = new ArrayList<>();
        for (int index = 0; index < chunkCount; index++) {
            chunkKeys.add(modelChunkKey(modelName, index));
        }
        return chunkKeys;
    }

    private ChunkSummary rebuildDownloadChunksFromModel(final String modelName, final long totalSize) throws Exception {
        ModelObjectInfo sourceObjectInfo = prepareLegacyModelSource(modelName);
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(sourceObjectInfo.objectName())
                        .build()
        )) {
            return writeDownloadChunks(modelName, sourceObjectInfo.size(), stream);
        }
    }

    private ModelObjectInfo prepareLegacyModelSource(final String modelName) throws Exception {
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(modelKey(modelName))
                            .build()
            );
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(bucket)
                            .object(legacyModelKey(modelName))
                            .source(CopySource.builder()
                                    .bucket(bucket)
                                    .object(modelKey(modelName))
                                    .build())
                            .build()
            );
            removeObjectIfExists(modelKey(modelName));
            return new ModelObjectInfo(legacyModelKey(modelName), stat.size());
        } catch (ErrorResponseException exception) {
            if (!"NoSuchKey".equals(exception.errorResponse().code())) {
                throw exception;
            }
        }

        StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(bucket)
                        .object(legacyModelKey(modelName))
                        .build()
        );
        return new ModelObjectInfo(legacyModelKey(modelName), stat.size());
    }

    private ChunkSummary writeDownloadChunks(final String modelName, final long totalSize, final InputStream source) throws Exception {
        long chunkSizeBytes = normalizedDownloadChunkSizeBytes();
        removeObjectsByPrefix(modelChunkPrefix(modelName));

        int chunkIndex = 0;
        long remaining = totalSize;
        while (remaining > 0) {
            long currentChunkSize = Math.min(chunkSizeBytes, remaining);
            BoundedInputStream chunkStream = new BoundedInputStream(source, currentChunkSize);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(modelChunkKey(modelName, chunkIndex))
                            .stream(chunkStream, currentChunkSize, -1)
                            .contentType("application/octet-stream")
                            .build()
            );
            if (chunkStream.consumed() != currentChunkSize) {
                throw new IOException("모델 다운로드 chunk 생성 중 원본 스트림이 예상보다 일찍 종료되었습니다.");
            }
            remaining -= currentChunkSize;
            chunkIndex += 1;
        }
        return new ChunkSummary(chunkIndex, totalSize);
    }

    private long normalizedDownloadChunkSizeBytes() {
        if (downloadChunkSizeBytes <= 0) {
            throw new IllegalStateException("model.download-chunk-size-bytes는 1 이상이어야 합니다.");
        }
        return downloadChunkSizeBytes;
    }

    private void removeObjectsByPrefix(final String prefix) throws Exception {
        Iterable<io.minio.Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .recursive(true)
                        .build()
        );
        for (io.minio.Result<Item> result : results) {
            Item item = result.get();
            if (!item.isDir()) {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucket)
                                .object(item.objectName())
                                .build()
                );
            }
        }
    }

    private void removeObjectIfExists(final String objectName) throws Exception {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build()
            );
        } catch (ErrorResponseException exception) {
            if (!"NoSuchKey".equals(exception.errorResponse().code())) {
                throw exception;
            }
        }
    }

    private boolean isSafeModelName(final String modelName) {
        if (modelName == null || modelName.isBlank() || !SAFE_NAME.matcher(modelName).matches()) {
            return false;
        }
        String lower = modelName.toLowerCase(Locale.ROOT);
        return !lower.equals(".")
                && !lower.equals("..")
                && !lower.startsWith(".uploads")
                && !lower.startsWith(".chunks");
    }

    private boolean isChunkObjectName(final String objectName) {
        return objectName != null
                && !objectName.isBlank()
                && !objectName.contains("/")
                && objectName.endsWith(".part");
    }

    public record ModelChunkUploadRes(String model_name, String upload_id, int chunk_index, int total_chunks, long size) {
    }

    public record ModelUploadCompleteRes(String model_name, long size, int chunk_count) {
    }

    public record ModelInfoRes(String model_name, String format, long size, int chunk_count) {
    }

    public record ModelDownload(String modelName, long size, InputStream stream) {
    }

    public record ModelDownloadInfo(String modelName, long size) {
    }

    public record ModelConfigUpdateReq(
            String model_name,
            Float temperature,
            Float top_p,
            Integer top_k,
            Integer max_output_tokens,
            String systemInstruction
    ) {
    }

    public record ModelConfigRes(
            String model_name,
            LocalDateTime config_date,
            Float temperature,
            Float top_p,
            Integer top_k,
            Integer max_output_tokens,
            String systemInstruction
    ) {
    }

    private record ModelObjectInfo(String objectName, long size) {
    }

    private record ChunkSummary(int count, long size) {
    }

    private record ModelNameParts(String modelName, String format) {
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

    private static class BoundedInputStream extends InputStream {
        private final InputStream delegate;
        private long remaining;
        private long consumed;

        private BoundedInputStream(final InputStream delegate, final long limit) {
            this.delegate = delegate;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            byte[] buffer = new byte[1];
            int read = read(buffer, 0, 1);
            return read == -1 ? -1 : buffer[0] & 0xff;
        }

        @Override
        public int read(final byte[] buffer, final int offset, final int length) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int maxLength = (int) Math.min(length, remaining);
            int read = delegate.read(buffer, offset, maxLength);
            if (read == -1) {
                return -1;
            }
            remaining -= read;
            consumed += read;
            return read;
        }

        private long consumed() {
            return consumed;
        }
    }
}
