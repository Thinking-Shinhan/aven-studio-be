package kr.co.daeun.avenstudiobe.domain.imagegeneration.service;

import kr.co.daeun.avenstudiobe.domain.asset.entity.Asset;
import kr.co.daeun.avenstudiobe.domain.asset.service.AssetService;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.repository.ProductRepository;
import kr.co.daeun.avenstudiobe.global.exception.ImageGenerationQueueFullException;
import kr.co.daeun.avenstudiobe.integration.google.GoogleAiImageProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ImageGenerationWorker {

    private static final String GENERATION_FAILED_MESSAGE = "이미지 생성 중 오류가 발생했습니다.";
    private static final int MAX_GOOGLE_GENERATION_ATTEMPTS = 2;
    private static final long GOOGLE_RETRY_DELAY_MILLIS = 800L;

    private final GoogleAiImageProvider imageProvider;
    private final AspectRatioImageCropper imageCropper;
    private final ProductRepository productRepository;
    private final AssetService assetService;
    private final ImageGenerationProgressService progressService;
    @Qualifier("imageGenerationJobExecutor")
    private final TaskExecutor jobExecutor;
    @Qualifier("imageGenerationTaskExecutor")
    private final TaskExecutor taskExecutor;

    public ImageGenerationWorker(
            GoogleAiImageProvider imageProvider,
            AspectRatioImageCropper imageCropper,
            ProductRepository productRepository,
            AssetService assetService,
            ImageGenerationProgressService progressService,
            @Qualifier("imageGenerationJobExecutor") TaskExecutor jobExecutor,
            @Qualifier("imageGenerationTaskExecutor") TaskExecutor taskExecutor
    ) {
        this.imageProvider = imageProvider;
        this.imageCropper = imageCropper;
        this.productRepository = productRepository;
        this.assetService = assetService;
        this.progressService = progressService;
        this.jobExecutor = jobExecutor;
        this.taskExecutor = taskExecutor;
    }

    public void submit(ImageGenerationWork work) {
        try {
            jobExecutor.execute(() -> process(work));
        } catch (TaskRejectedException e) {
            progressService.markFailed(work.jobId(), "이미지 생성 작업 대기열이 가득 찼습니다.", elapsedMs(work));
            throw new ImageGenerationQueueFullException();
        }
    }

    private void process(ImageGenerationWork work) {
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        try {
            progressService.markProcessing(work.jobId());

            CompletableFuture<?>[] futures = work.tasks().stream()
                    .map(task -> CompletableFuture
                            .runAsync(() -> generateAndSave(work, task), taskExecutor)
                            .exceptionally(error -> {
                                failures.add(unwrap(error));
                                return null;
                            }))
                    .toArray(CompletableFuture[]::new);

            CompletableFuture.allOf(futures).join();

            if (failures.isEmpty()) {
                progressService.markSucceeded(work.jobId(), elapsedMs(work));
            } else {
                failures.forEach(error -> log.error("이미지 생성 작업 일부 실패: jobId={}", work.jobId(), error));
                progressService.markFailed(
                        work.jobId(),
                        resolveFailureMessage(failures),
                        elapsedMs(work)
                );
            }
        } catch (RuntimeException e) {
            log.error("이미지 생성 작업 실패: jobId={}", work.jobId(), e);
            progressService.markFailed(
                    work.jobId(),
                    resolveFailureMessage(List.of(e)),
                    elapsedMs(work)
            );
        }
    }

    private String resolveFailureMessage(Iterable<Throwable> failures) {
        String messages = collectFailureMessages(failures);

        if (containsAny(messages, "status=429", "resource_exhausted", "quota")) {
            return "[GOOGLE_API_QUOTA_EXCEEDED] Google AI 사용량 한도를 초과했습니다.";
        }
        if (containsAny(messages, "status=500", "status=502", "status=503", "status=504",
                "unavailable", "overloaded", "temporarily unavailable")) {
            return "[GOOGLE_API_TEMPORARY_UNAVAILABLE] Google AI 이미지 생성 서버가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.";
        }
        if (containsAny(messages, "status=401", "status=403", "permission_denied", "api key")) {
            return "[GOOGLE_API_AUTH_FAILED] Google AI 인증 또는 모델 사용 권한을 확인해주세요.";
        }
        if (containsAny(messages, "status=400", "invalid_argument", "invalid json payload", "unknown name")) {
            return "[GOOGLE_API_INVALID_REQUEST] Google AI 이미지 생성 요청 형식이 올바르지 않습니다.";
        }
        if (containsAny(messages, "resourceaccessexception", "readtimeoutexception", "connection", "timed out")) {
            return "[GOOGLE_API_CONNECTION_FAILED] Google AI 연결 또는 응답 대기 중 오류가 발생했습니다.";
        }
        if (containsAny(messages, "storageexception", "storage.googleapis.com", "gcs")) {
            return "[GCS_UPLOAD_FAILED] 생성 이미지를 저장하지 못했습니다.";
        }
        if (containsAny(messages, "aspectratio", "aspect ratio", "이미지 비율", "크롭")) {
            return "[ASPECT_RATIO_PROCESSING_FAILED] 생성 이미지 비율 처리에 실패했습니다.";
        }
        return GENERATION_FAILED_MESSAGE;
    }

    private String collectFailureMessages(Iterable<Throwable> failures) {
        StringBuilder messages = new StringBuilder();
        for (Throwable failure : failures) {
            Throwable current = failure;
            while (current != null) {
                messages.append(' ')
                        .append(current.getClass().getSimpleName())
                        .append(' ')
                        .append(current.getMessage());
                current = current.getCause();
            }
        }
        return messages.toString().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void generateAndSave(ImageGenerationWork work, ImageGenerationWork.Task task) {
        GoogleAiImageProvider.GeneratedImage generatedImage = generateAndCorrectAspectRatio(work, task);
        Product product = productRepository.findById(work.productId())
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        Asset asset = assetService.saveGeneratedImage(
                generatedImage.bytes(),
                generatedImage.contentType(),
                product,
                work.jobId(),
                task.prompt(),
                task.assetRole(),
                task.description()
        );
        progressService.recordGeneratedResult(work.jobId(), asset, task.outputIndex());
    }

    private GoogleAiImageProvider.GeneratedImage generateAndCorrectAspectRatio(
            ImageGenerationWork work,
            ImageGenerationWork.Task task
    ) {
        if (task.aspectRatio() == null || task.aspectRatio().isBlank()) {
            throw new IllegalStateException(
                    "이미지 생성 작업에 aspectRatio가 전달되지 않았습니다. jobId=" + work.jobId()
            );
        }

        log.info(
                "Google AI 이미지 생성 요청: jobId={}, outputIndex={}, model={}, cameraAngle={}, framing={}, requestedAspectRatio={}, referenceCount={}",
                work.jobId(),
                task.outputIndex(),
                imageProvider.getImageModel(),
                task.cameraAngle(),
                task.framing(),
                task.aspectRatio(),
                task.referenceImages().size()
        );

        GoogleAiImageProvider.GeneratedImage generatedImage = generateWithRetry(work, task);
        AspectRatioImageCropper.CropResult cropResult =
                imageCropper.cropToAspectRatio(generatedImage, task.aspectRatio());

        if (cropResult.cropped()) {
            log.info(
                    "생성 이미지 비율 중앙 크롭: jobId={}, outputIndex={}, requestedAspectRatio={}, originalWidth={}, originalHeight={}, croppedWidth={}, croppedHeight={}, correction=CENTER_CROP",
                    work.jobId(),
                    task.outputIndex(),
                    task.aspectRatio(),
                    generatedImage.width(),
                    generatedImage.height(),
                    cropResult.image().width(),
                    cropResult.image().height()
            );
        } else {
            log.info(
                    "생성 이미지 비율 확인: jobId={}, outputIndex={}, requestedAspectRatio={}, generatedWidth={}, generatedHeight={}, correction=NONE",
                    work.jobId(),
                    task.outputIndex(),
                    task.aspectRatio(),
                    generatedImage.width(),
                    generatedImage.height()
            );
        }
        return cropResult.image();
    }

    private GoogleAiImageProvider.GeneratedImage generateWithRetry(
            ImageGenerationWork work,
            ImageGenerationWork.Task task
    ) {
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= MAX_GOOGLE_GENERATION_ATTEMPTS; attempt++) {
            try {
                staggerFirstAttempt(task, attempt);
                return imageProvider.generate(
                        task.prompt(),
                        task.referenceImages(),
                        task.aspectRatio(),
                        task.finalInstruction()
                );
            } catch (RuntimeException e) {
                lastError = e;
                if (!isRetryableGoogleFailure(e) || attempt == MAX_GOOGLE_GENERATION_ATTEMPTS) {
                    throw e;
                }

                long delayMillis = GOOGLE_RETRY_DELAY_MILLIS * attempt;
                log.warn(
                        "Google AI 이미지 생성 재시도 예정: jobId={}, outputIndex={}, attempt={}, delayMillis={}, cause={}",
                        work.jobId(),
                        task.outputIndex(),
                        attempt + 1,
                        delayMillis,
                        e.getMessage()
                );
                sleep(delayMillis);
            }
        }

        throw lastError == null ? new IllegalStateException("Google AI 이미지 생성에 실패했습니다.") : lastError;
    }

    private void staggerFirstAttempt(ImageGenerationWork.Task task, int attempt) {
        if (attempt > 1 || task.outputIndex() <= 0) {
            return;
        }
        sleep(Math.min(900L, task.outputIndex() * 250L));
    }

    private boolean isRetryableGoogleFailure(Throwable error) {
        String messages = collectFailureMessages(List.of(error));
        return containsAny(messages,
                "status=429",
                "status=500",
                "status=502",
                "status=503",
                "status=504",
                "resource_exhausted",
                "quota",
                "unavailable",
                "overloaded",
                "temporarily unavailable",
                "resourceaccessexception",
                "readtimeoutexception",
                "connection",
                "timed out"
        );
    }

    private void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("이미지 생성 재시도 대기 중 인터럽트가 발생했습니다.", e);
        }
    }

    private Throwable unwrap(Throwable error) {
        return error.getCause() == null ? error : error.getCause();
    }

    private int elapsedMs(ImageGenerationWork work) {
        return Math.toIntExact(System.currentTimeMillis() - work.startedAt());
    }
}
