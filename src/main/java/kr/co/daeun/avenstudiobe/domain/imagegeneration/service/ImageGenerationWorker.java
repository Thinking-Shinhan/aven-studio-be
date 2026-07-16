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

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
@Slf4j
public class ImageGenerationWorker {

    private static final String GENERATION_FAILED_MESSAGE = "이미지 생성 중 오류가 발생했습니다.";
    private static final int MAX_ASPECT_RATIO_RETRIES = 1;
    private static final double ASPECT_RATIO_TOLERANCE = 0.01;

    private final GoogleAiImageProvider imageProvider;
    private final ProductRepository productRepository;
    private final AssetService assetService;
    private final ImageGenerationProgressService progressService;
    @Qualifier("imageGenerationJobExecutor")
    private final TaskExecutor jobExecutor;
    @Qualifier("imageGenerationTaskExecutor")
    private final TaskExecutor taskExecutor;

    public ImageGenerationWorker(
            GoogleAiImageProvider imageProvider,
            ProductRepository productRepository,
            AssetService assetService,
            ImageGenerationProgressService progressService,
            @Qualifier("imageGenerationJobExecutor") TaskExecutor jobExecutor,
            @Qualifier("imageGenerationTaskExecutor") TaskExecutor taskExecutor
    ) {
        this.imageProvider = imageProvider;
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
                progressService.markFailed(work.jobId(), GENERATION_FAILED_MESSAGE, elapsedMs(work));
            }
        } catch (RuntimeException e) {
            log.error("이미지 생성 작업 실패: jobId={}", work.jobId(), e);
            progressService.markFailed(work.jobId(), GENERATION_FAILED_MESSAGE, elapsedMs(work));
        }
    }

    private void generateAndSave(ImageGenerationWork work, ImageGenerationWork.Task task) {
        GoogleAiImageProvider.GeneratedImage generatedImage = generateWithAspectRatioValidation(work, task);
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

    private GoogleAiImageProvider.GeneratedImage generateWithAspectRatioValidation(
            ImageGenerationWork work,
            ImageGenerationWork.Task task
    ) {
        GoogleAiImageProvider.GeneratedImage lastImage = null;

        for (int retryCount = 0; retryCount <= MAX_ASPECT_RATIO_RETRIES; retryCount++) {
            String finalInstruction = retryCount == 0
                    ? task.finalInstruction()
                    : appendRatioCorrection(task.finalInstruction(), task.aspectRatio(), lastImage);

            log.info(
                    "Google AI 이미지 생성 요청: jobId={}, outputIndex={}, model={}, cameraAngle={}, framing={}, requestedAspectRatio={}, referenceCount={}, retryCount={}",
                    work.jobId(),
                    task.outputIndex(),
                    imageProvider.getImageModel(),
                    task.cameraAngle(),
                    task.framing(),
                    task.aspectRatio(),
                    task.referenceImages().size(),
                    retryCount
            );

            lastImage = imageProvider.generate(
                    task.prompt(),
                    task.referenceImages(),
                    task.aspectRatio(),
                    finalInstruction
            );

            double actualRatio = (double) lastImage.width() / lastImage.height();
            log.info(
                    "Google AI 이미지 생성 응답: jobId={}, outputIndex={}, requestedAspectRatio={}, generatedWidth={}, generatedHeight={}, actualAspectRatio={}, retryCount={}",
                    work.jobId(),
                    task.outputIndex(),
                    task.aspectRatio(),
                    lastImage.width(),
                    lastImage.height(),
                    actualRatio,
                    retryCount
            );

            if (matchesRequestedAspectRatio(lastImage, task.aspectRatio())) {
                return lastImage;
            }
        }

        throw new IllegalStateException(
                "생성 이미지 비율이 요청과 일치하지 않습니다. requested=" + task.aspectRatio()
                        + ", actual=" + lastImage.width() + "x" + lastImage.height()
        );
    }

    private boolean matchesRequestedAspectRatio(
            GoogleAiImageProvider.GeneratedImage image,
            String requestedAspectRatio
    ) {
        if (requestedAspectRatio == null || requestedAspectRatio.isBlank()) {
            return true;
        }

        String[] ratioParts = requestedAspectRatio.split(":");
        if (ratioParts.length != 2) {
            throw new IllegalArgumentException("지원하지 않는 이미지 비율 형식입니다: " + requestedAspectRatio);
        }

        double expectedRatio = Double.parseDouble(ratioParts[0]) / Double.parseDouble(ratioParts[1]);
        double actualRatio = (double) image.width() / image.height();
        double relativeError = Math.abs(actualRatio - expectedRatio) / expectedRatio;
        return relativeError <= ASPECT_RATIO_TOLERANCE;
    }

    private String appendRatioCorrection(
            String finalInstruction,
            String requestedAspectRatio,
            GoogleAiImageProvider.GeneratedImage previousImage
    ) {
        String correction = """

                [RETRY CORRECTION]
                The previous output was incorrectly generated as %dx%d.
                Discard that canvas layout and generate an exact %s image.
                """.formatted(previousImage.width(), previousImage.height(), requestedAspectRatio).stripTrailing();

        if (finalInstruction == null || finalInstruction.isBlank()) {
            return correction.strip();
        }
        return finalInstruction + "\n\n" + correction.strip();
    }

    private Throwable unwrap(Throwable error) {
        return error.getCause() == null ? error : error.getCause();
    }

    private int elapsedMs(ImageGenerationWork work) {
        return Math.toIntExact(System.currentTimeMillis() - work.startedAt());
    }
}
