package kr.co.daeun.avenstudiobe.domain.imagegeneration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.daeun.avenstudiobe.domain.asset.entity.Asset;
import kr.co.daeun.avenstudiobe.domain.asset.repository.AssetRepository;
import kr.co.daeun.avenstudiobe.domain.asset.service.AssetService;
import kr.co.daeun.avenstudiobe.domain.brand.entity.Brand;
import kr.co.daeun.avenstudiobe.domain.brand.repository.BrandRepository;
import kr.co.daeun.avenstudiobe.domain.brandmood.entity.BrandMood;
import kr.co.daeun.avenstudiobe.domain.brandmood.repository.BrandMoodRepository;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ImageGenerationJobCreateReqDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ImageGenerationJobRespDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ImageGenerationResultRespDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.entity.ImageGenerationJob;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.entity.ImageGenerationResult;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.entity.ReferenceAsset;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.repository.ImageGenerationJobRepository;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.repository.ImageGenerationResultRepository;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.repository.ReferenceAssetRepository;
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
import org.springframework.web.client.RestClient;

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
    private final BrandMoodRepository brandMoodRepository;
    private final AssetRepository assetRepository;
    private final ReferenceAssetRepository referenceAssetRepository;
    private final UsersRepository usersRepository;
    private final AssetService assetService;
    private final GcsService gcsService;
    private final GoogleAiImageProvider googleAiImageProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

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
        BrandMood brandMood = findBrandMood(request.getBrandMoodId(), brand.getId());
        List<ReferenceAssetInput> referenceAssets = resolveReferenceAssets(request.getReferenceJson());
        String compiledPrompt = compilePrompt(brandMood, request.getPrompt(), referenceAssets);

        ImageGenerationJob job = savePendingJob(request, user, brand, product, compiledPrompt);
        long startedAt = System.currentTimeMillis();

        try {
            markProcessing(job.getId());

            List<GoogleAiImageProvider.GeneratedImage> generatedImages = generateImages(
                    compiledPrompt,
                    request.getRequestedCount(),
                    toProviderReferenceImages(referenceAssets)
            );

            if (gcsSaveEnabled) {
                List<ImageGenerationResult> results = saveGeneratedResults(
                        job.getId(),
                        product.getId(),
                        generatedImages,
                        compiledPrompt,
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
            Product product,
            String compiledPrompt
    ) {
        ImageGenerationJob job = ImageGenerationJob.create(
                brand,
                product,
                request.getBrandMoodId(),
                user,
                request.getCutType(),
                normalizeGenerationMode(request.getGenerationMode()),
                compiledPrompt,
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

    private List<GoogleAiImageProvider.GeneratedImage> generateImages(
            String prompt,
            int requestedCount,
            List<GoogleAiImageProvider.ReferenceImage> referenceImages
    ) {
        List<CompletableFuture<GoogleAiImageProvider.GeneratedImage>> futures = new ArrayList<>();

        for (int i = 0; i < requestedCount; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> googleAiImageProvider.generate(prompt, referenceImages)));
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

    private BrandMood findBrandMood(Long brandMoodId, Long brandId) {
        BrandMood brandMood = brandMoodRepository.findById(brandMoodId)
                .orElseThrow(() -> new IllegalArgumentException("브랜드 무드를 찾을 수 없습니다."));

        if (!brandMood.getBrand().getId().equals(brandId)) {
            throw new IllegalArgumentException("브랜드에 속한 무드가 아닙니다.");
        }

        if (!Boolean.TRUE.equals(brandMood.getActive())) {
            throw new IllegalArgumentException("활성화된 브랜드 무드가 아닙니다.");
        }

        return brandMood;
    }

    private String compilePrompt(BrandMood brandMood, String userPrompt, List<ReferenceAssetInput> referenceAssets) {
        String brandInstruction = brandMood.getBrandInstruction();
        if (brandInstruction == null || brandInstruction.isBlank()) {
            throw new IllegalArgumentException("브랜드 무드 지시문이 비어 있습니다.");
        }

        return """
                [BRAND INSTRUCTION]
                %s

                %s

                [IMAGE GENERATION REQUEST]
                %s
                """.formatted(
                brandInstruction.trim(),
                buildReferenceInstruction(referenceAssets),
                userPrompt.trim()
        );
    }

    private String buildReferenceInstruction(List<ReferenceAssetInput> referenceAssets) {
        if (referenceAssets == null || referenceAssets.isEmpty()) {
            return "[REFERENCE IMAGE ROLE RULES]\nNo reference images are attached.";
        }

        StringBuilder keys = new StringBuilder();
        for (ReferenceAssetInput referenceAsset : referenceAssets) {
            keys.append("- ")
                    .append(referenceAsset.key())
                    .append(" = ")
                    .append(referenceAsset.sourceLabel())
                    .append("\n");
        }

        return """
                [REFERENCE IMAGE ROLE RULES]
                The attached reference images are provided with explicit input keys.
                Interpret each reference image only according to its input key.
                Never swap the role of each reference image.

                productImages = GARMENT_REFERENCE.
                productImages are the product accuracy references. The final model must wear the exact garment from productImages. Preserve the actual color, shape, fit, silhouette, length, material, logo position, washed fabric texture, drape, and construction details. Do not redesign the garment. Do not change the product category. Do not change the product color.

                modelImage = MODEL_REFERENCE.
                modelImage is the identity reference. Preserve face shape, skin tone, body proportion, hair impression, age impression, and overall human mood. If productImages provide only part of the outfit, use modelImage only for missing styling elements.

                poseImage = POSE_COMPOSITION_REFERENCE.
                poseImage is the exact pose and camera composition reference. Use poseImage only for body pose, body direction, camera angle, framing, crop range, and model placement inside the frame. Do not copy poseImage face, identity, clothing, gray 3D material, background, color tone, or brand mood.

                backgroundImage = BACKGROUND_REFERENCE.
                backgroundImage is the background mood reference. Use backgroundImage for background color, lighting feeling, spatial depth, material mood, and overall atmosphere. Do not copy people, text, logos, signs, or unnecessary objects from backgroundImage.

                [ATTACHED REFERENCE IMAGE KEYS]
                %s
                """.formatted(keys.toString().trim());
    }

    private List<ReferenceAssetInput> resolveReferenceAssets(String referenceJson) {
        JsonNode root = parseReferenceJson(referenceJson);
        if (root == null || root.isMissingNode() || root.isNull() || root.isEmpty()) {
            return List.of();
        }

        List<ReferenceAssetInput> references = new ArrayList<>();
        addProductImages(references, root.path("productImages"));
        addSingleReference(references, "modelImage", root.path("modelImage"));
        addSingleReference(references, "poseImage", root.path("poseImage"));
        addSingleReference(references, "backgroundImage", root.path("backgroundImage"));
        addServiceReference(references, "modelImage", root.path("modelReferenceId"));
        addServiceReference(references, "poseImage", root.path("poseReferenceId"));
        addServiceReference(references, "backgroundImage", root.path("backgroundReferenceId"));
        addServiceReference(references, "shotReferenceImage", root.path("shotReferenceId"));

        if (references.size() > 14) {
            throw new IllegalArgumentException("참조 이미지는 최대 14장까지만 첨부할 수 있습니다.");
        }

        return references;
    }

    private JsonNode parseReferenceJson(String referenceJson) {
        if (referenceJson == null || referenceJson.isBlank()) {
            return objectMapper.createObjectNode();
        }

        try {
            return objectMapper.readTree(referenceJson);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("referenceJson 형식이 올바르지 않습니다.", e);
        }
    }

    private void addProductImages(List<ReferenceAssetInput> references, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }

        if (node.isArray()) {
            int index = 0;
            for (JsonNode item : node) {
                addReference(references, "productImages[" + index + "]", item);
                index++;
            }
            return;
        }

        addReference(references, "productImages[0]", node);
    }

    private void addSingleReference(List<ReferenceAssetInput> references, String key, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }

        addReference(references, key, node);
    }

    private void addReference(List<ReferenceAssetInput> references, String key, JsonNode node) {
        Long assetId = extractAssetId(node);
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("참조 이미지를 찾을 수 없습니다. assetId=" + assetId));

        references.add(new ReferenceAssetInput(
                key,
                "assetId " + asset.getId(),
                gcsService.download(asset.getObjectName()),
                asset.getContentType()
        ));
    }

    private void addServiceReference(List<ReferenceAssetInput> references, String key, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }

        Long referenceAssetId = extractAssetId(node);
        ReferenceAsset referenceAsset = referenceAssetRepository.findById(referenceAssetId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "서비스 레퍼런스 이미지를 찾을 수 없습니다. referenceAssetId=" + referenceAssetId
                ));

        if (!Boolean.TRUE.equals(referenceAsset.getActive())) {
            throw new IllegalArgumentException("비활성 서비스 레퍼런스 이미지입니다. referenceAssetId=" + referenceAssetId);
        }

        references.add(new ReferenceAssetInput(
                key,
                "referenceAssetId " + referenceAsset.getId() + " (" + referenceAsset.getReferenceKey() + ")",
                readReferenceAssetBytes(referenceAsset),
                referenceAsset.getContentType()
        ));
    }

    private byte[] readReferenceAssetBytes(ReferenceAsset referenceAsset) {
        if (referenceAsset.getObjectName() != null && !referenceAsset.getObjectName().isBlank()) {
            return gcsService.download(referenceAsset.getObjectName());
        }

        if (referenceAsset.getImageUrl() == null || referenceAsset.getImageUrl().isBlank()) {
            throw new IllegalArgumentException("서비스 레퍼런스 이미지 URL이 비어 있습니다.");
        }

        return restClient.get()
                .uri(referenceAsset.getImageUrl())
                .retrieve()
                .body(byte[].class);
    }

    private Long extractAssetId(JsonNode node) {
        if (node.isNumber()) {
            return node.asLong();
        }

        if (node.isObject() && node.hasNonNull("assetId")) {
            return node.path("assetId").asLong();
        }

        throw new IllegalArgumentException("referenceJson에는 assetId 숫자 또는 {\"assetId\": number} 형식만 사용할 수 있습니다.");
    }

    private List<GoogleAiImageProvider.ReferenceImage> toProviderReferenceImages(
            List<ReferenceAssetInput> referenceAssets
    ) {
        if (referenceAssets == null || referenceAssets.isEmpty()) {
            return List.of();
        }

        return referenceAssets.stream()
                .map(referenceAsset -> new GoogleAiImageProvider.ReferenceImage(
                        referenceAsset.key(),
                        referenceAsset.bytes(),
                        referenceAsset.contentType()
                ))
                .toList();
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
        if (requestedCount < 1 || requestedCount > 4) {
            throw new IllegalArgumentException("테스트 단계에서는 최대 4장까지만 생성할 수 있습니다.");
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

    private record ReferenceAssetInput(
            String key,
            String sourceLabel,
            byte[] bytes,
            String contentType
    ) {
    }
}
