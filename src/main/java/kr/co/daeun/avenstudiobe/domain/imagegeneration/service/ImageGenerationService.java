package kr.co.daeun.avenstudiobe.domain.imagegeneration.service;

import kr.co.daeun.avenstudiobe.domain.asset.entity.Asset;
import kr.co.daeun.avenstudiobe.domain.asset.service.AssetService;
import kr.co.daeun.avenstudiobe.domain.brand.entity.Brand;
import kr.co.daeun.avenstudiobe.domain.brand.repository.BrandRepository;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ImageGenerationJobCreateReqDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ImageGenerationJobRespDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ImageGenerationResultRespDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.entity.ImageGenerationJob;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.entity.ImageGenerationResult;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.repository.ImageGenerationJobRepository;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.repository.ImageGenerationResultRepository;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.repository.ProductRepository;
import kr.co.daeun.avenstudiobe.domain.user.entity.User;
import kr.co.daeun.avenstudiobe.domain.user.repository.UsersRepository;
import kr.co.daeun.avenstudiobe.global.storage.GcsService;
import kr.co.daeun.avenstudiobe.integration.google.GoogleAiImageProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
@RequiredArgsConstructor
public class ImageGenerationService {

    private final ImageGenerationJobRepository jobRepository;
    private final ImageGenerationResultRepository resultRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final UsersRepository usersRepository;
    private final AssetService assetService;
    private final GcsService gcsService;
    private final GoogleAiImageProvider googleAiImageProvider;

    @Value("${image-generation.gcs-save-enabled:true}")
    private boolean gcsSaveEnabled;

    public ImageGenerationJobRespDto createJob(ImageGenerationJobCreateReqDto request, String loginId) {
        validateRequestedCount(request.getRequestedCount());

        User user = usersRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Brand brand = brandRepository.findById(request.getBrandId())
                .orElseThrow(() -> new IllegalArgumentException("브랜드를 찾을 수 없습니다."));
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        ImageGenerationJob job = savePendingJob(request, user, brand, product);
        long startedAt = System.currentTimeMillis();

        try {
            markProcessing(job.getId());

            List<GoogleAiImageProvider.GeneratedImage> generatedImages = generateImages(
                    request.getPrompt(),
                    request.getRequestedCount()
            );

            if (gcsSaveEnabled) {
                List<ImageGenerationResult> results = saveGeneratedResults(
                        job.getId(),
                        product.getId(),
                        generatedImages,
                        request.getPrompt(),
                        startedAt
                );

                return toResponse(job.getId(), results);
            }

            List<ImageGenerationResult> results = saveGeneratedResultsWithoutGcs(
                    job.getId(),
                    generatedImages.size(),
                    startedAt
            );

            return toResponseWithInlineImages(job.getId(), results, generatedImages);
        } catch (RuntimeException e) {
            markFailed(job.getId(), e.getMessage(), elapsedMs(startedAt));
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public ImageGenerationJobRespDto getJob(Long jobId) {
        ImageGenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("이미지 생성 Job을 찾을 수 없습니다."));

        List<ImageGenerationResult> results = resultRepository.findByJobIdOrderByOutputIndexAsc(jobId);
        List<ImageGenerationResultRespDto> resultDtos = toResultDtos(results);

        return ImageGenerationJobRespDto.from(job, resultDtos);
    }

    @Transactional
    public ImageGenerationJob savePendingJob(
            ImageGenerationJobCreateReqDto request,
            User user,
            Brand brand,
            Product product
    ) {
        ImageGenerationJob job = ImageGenerationJob.create(
                brand,
                product,
                request.getBrandMoodId(),
                user,
                request.getCutType(),
                normalizeGenerationMode(request.getGenerationMode()),
                request.getPrompt(),
                request.getNegativePrompt(),
                normalizeJson(request.getUserOptionsJson()),
                normalizeJson(request.getReferenceJson()),
                googleAiImageProvider.getImageModel(),
                request.getRequestedCount()
        );

        return jobRepository.save(job);
    }

    @Transactional
    public void markProcessing(Long jobId) {
        ImageGenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("이미지 생성 Job을 찾을 수 없습니다."));
        job.markProcessing();
        jobRepository.save(job);
    }

    @Transactional
    public List<ImageGenerationResult> saveGeneratedResults(
            Long jobId,
            Long productId,
            List<GoogleAiImageProvider.GeneratedImage> generatedImages,
            String prompt,
            long startedAt
    ) {
        ImageGenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("이미지 생성 Job을 찾을 수 없습니다."));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        List<ImageGenerationResult> results = new ArrayList<>();

        for (int i = 0; i < generatedImages.size(); i++) {
            GoogleAiImageProvider.GeneratedImage generatedImage = generatedImages.get(i);
            Asset asset = assetService.saveGeneratedImage(
                    generatedImage.bytes(),
                    generatedImage.contentType(),
                    product,
                    job.getId(),
                    prompt
            );

            ImageGenerationResult result = ImageGenerationResult.generated(job, asset, i);
            results.add(resultRepository.save(result));
        }

        job.markSucceeded(results.size(), elapsedMs(startedAt));
        jobRepository.save(job);

        return results;
    }

    @Transactional
    public List<ImageGenerationResult> saveGeneratedResultsWithoutGcs(
            Long jobId,
            int generatedImageCount,
            long startedAt
    ) {
        ImageGenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("이미지 생성 Job을 찾을 수 없습니다."));

        List<ImageGenerationResult> results = new ArrayList<>();

        for (int i = 0; i < generatedImageCount; i++) {
            ImageGenerationResult result = ImageGenerationResult.generatedWithoutSave(job, i);
            results.add(resultRepository.save(result));
        }

        job.markSucceeded(results.size(), elapsedMs(startedAt));
        jobRepository.save(job);

        return results;
    }

    @Transactional
    public void markFailed(Long jobId, String errorMessage, int latencyMs) {
        ImageGenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("이미지 생성 Job을 찾을 수 없습니다."));
        job.markFailed(errorMessage, latencyMs);
        jobRepository.save(job);
    }

    private List<GoogleAiImageProvider.GeneratedImage> generateImages(String prompt, int requestedCount) {
        List<CompletableFuture<GoogleAiImageProvider.GeneratedImage>> futures = new ArrayList<>();

        for (int i = 0; i < requestedCount; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> googleAiImageProvider.generate(prompt)));
        }

        return futures.stream()
                .map(this::joinGeneratedImage)
                .toList();
    }

    private GoogleAiImageProvider.GeneratedImage joinGeneratedImage(
            CompletableFuture<GoogleAiImageProvider.GeneratedImage> future
    ) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private ImageGenerationJobRespDto toResponse(Long jobId, List<ImageGenerationResult> results) {
        ImageGenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("이미지 생성 Job을 찾을 수 없습니다."));

        return ImageGenerationJobRespDto.from(job, toResultDtos(results));
    }

    private ImageGenerationJobRespDto toResponseWithInlineImages(
            Long jobId,
            List<ImageGenerationResult> results,
            List<GoogleAiImageProvider.GeneratedImage> generatedImages
    ) {
        ImageGenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("이미지 생성 Job을 찾을 수 없습니다."));

        List<ImageGenerationResultRespDto> resultDtos = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            GoogleAiImageProvider.GeneratedImage generatedImage = generatedImages.get(i);
            String imageBase64 = "data:" + generatedImage.contentType() + ";base64,"
                    + Base64.getEncoder().encodeToString(generatedImage.bytes());
            resultDtos.add(ImageGenerationResultRespDto.from(results.get(i), null, imageBase64));
        }

        return ImageGenerationJobRespDto.from(job, resultDtos);
    }

    private List<ImageGenerationResultRespDto> toResultDtos(List<ImageGenerationResult> results) {
        return results.stream()
                .map(result -> {
                    String imageUrl = null;
                    if (result.getAsset() != null) {
                        imageUrl = gcsService.generateSignedUrl(result.getAsset().getObjectName());
                    }
                    return ImageGenerationResultRespDto.from(result, imageUrl);
                })
                .toList();
    }

    private void validateRequestedCount(int requestedCount) {
        if (requestedCount < 1 || requestedCount > 2) {
            throw new IllegalArgumentException("테스트 단계에서는 최대 2장까지만 생성할 수 있습니다.");
        }
    }

    private String normalizeGenerationMode(String generationMode) {
        if (generationMode == null || generationMode.isBlank()) {
            return "PARALLEL";
        }

        return generationMode.trim().toUpperCase();
    }

    private String normalizeJson(String json) {
        if (json == null || json.isBlank()) {
            return "{}";
        }

        return json;
    }

    private int elapsedMs(long startedAt) {
        return Math.toIntExact(System.currentTimeMillis() - startedAt);
    }
}
