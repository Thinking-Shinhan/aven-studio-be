package kr.co.daeun.avenstudiobe.domain.imagegeneration.service;

import kr.co.daeun.avenstudiobe.domain.asset.entity.Asset;
import kr.co.daeun.avenstudiobe.domain.asset.repository.AssetRepository;
import kr.co.daeun.avenstudiobe.domain.brand.entity.Brand;
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
import kr.co.daeun.avenstudiobe.domain.product.entity.Size;
import kr.co.daeun.avenstudiobe.domain.product.entity.SizeMeasurement;
import kr.co.daeun.avenstudiobe.domain.product.repository.ProductRepository;
import kr.co.daeun.avenstudiobe.domain.product.repository.SizeMeasurementRepository;
import kr.co.daeun.avenstudiobe.domain.product.repository.SizeRepository;
import kr.co.daeun.avenstudiobe.domain.user.entity.User;
import kr.co.daeun.avenstudiobe.domain.user.repository.UsersRepository;
import kr.co.daeun.avenstudiobe.global.storage.GcsService;
import kr.co.daeun.avenstudiobe.global.exception.ImageGenerationJobNotFoundException;
import kr.co.daeun.avenstudiobe.integration.google.GoogleAiImageProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ImageGenerationService {

    private final ImageGenerationJobRepository jobRepository;
    private final ImageGenerationResultRepository resultRepository;
    private final ProductRepository productRepository;
    private final BrandMoodRepository brandMoodRepository;
    private final AssetRepository assetRepository;
    private final ReferenceAssetRepository referenceAssetRepository;
    private final SizeRepository sizeRepository;
    private final SizeMeasurementRepository sizeMeasurementRepository;
    private final UsersRepository usersRepository;
    private final ImageGenerationWorker imageGenerationWorker;
    private final GcsService gcsService;
    private final GoogleAiImageProvider googleAiImageProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    @Value("${image-generation.gcs-save-enabled:true}")
    private boolean gcsSaveEnabled = true;

    private static final Map<String, String> COLOR_TEMPERATURE_LABELS = Map.of(
            "WARM", "웜톤",
            "NEUTRAL", "뉴트럴",
            "COOL", "쿨톤"
    );
    private static final Map<String, String> CAMERA_ANGLE_LABELS = Map.of(
            "EYE_LEVEL_FRONT", "정면",
            "LEFT_THREE_QUARTER_45", "좌측 45도 사선",
            "RIGHT_THREE_QUARTER_45", "우측 45도 사선",
            "BACK_VIEW", "후면"
    );
    private static final Map<String, String> CAMERA_ANGLE_INSTRUCTIONS = Map.of(
            "EYE_LEVEL_FRONT", """
                    모델의 자세와 신체 방향을 유지한 상태에서 카메라를 모델의 신체 정면 앞에 배치한다.
                    모델 얼굴과 상품 앞면이 보여야 한다.
                    피사체가 사선이나 후면으로 회전하지 않도록 한다.
                    """.strip(),
            "LEFT_THREE_QUARTER_45", """
                    모델의 자세와 신체 방향을 유지한 상태에서 카메라만 모델의 정면 기준 좌측 45도 위치로 이동한다.
                    모델이 카메라를 정면으로 보기 위해 몸이나 고개를 돌리지 않도록 한다.
                    상품 앞면과 모델의 왼쪽 측면이 함께 보여야 한다.
                    """.strip(),
            "RIGHT_THREE_QUARTER_45", """
                    모델의 자세와 신체 방향을 유지한 상태에서 카메라만 모델의 정면 기준 우측 45도 위치로 이동한다.
                    모델이 카메라를 정면으로 보기 위해 몸이나 고개를 돌리지 않도록 한다.
                    상품 앞면과 모델의 오른쪽 측면이 함께 보여야 한다.
                    """.strip(),
            "BACK_VIEW", """
                    모델의 자세와 신체 방향을 유지한 상태에서 카메라만 모델의 등 뒤 정중앙으로 이동한다.
                    모델의 신체 정면과 시선은 카메라 반대 방향을 향해야 한다.
                    결과에는 모델의 뒷통수, 등, 상품 후면이 보여야 한다.
                    얼굴, 가슴과 상품 앞면이 보이면 안 된다.
                    모델이 카메라를 보기 위해 뒤돌거나 고개를 돌리거나 어깨 너머로 바라보면 안 된다.
                    포즈 레퍼런스의 팔, 다리, 어깨, 골반과 체중 이동은 그대로 유지한다.
                    """.strip()
    );
    private static final Map<String, String> CAMERA_ANGLE_ROLE_SUFFIXES = Map.of(
            "EYE_LEVEL_FRONT", "FRONT",
            "LEFT_THREE_QUARTER_45", "LEFT_45",
            "RIGHT_THREE_QUARTER_45", "RIGHT_45",
            "BACK_VIEW", "BACK"
    );
    private static final Map<String, String> FRAMING_LABELS = Map.of(
            "FULL_BODY", "전신",
            "THREE_QUARTER_BODY", "3/4 전신",
            "HALF_BODY", "반신",
            "CLOSE_UP", "클로즈업",
            "WIDE_MARGIN", "여백 강조"
    );
    private static final Map<String, String> FRAMING_INSTRUCTIONS = Map.of(
            "FULL_BODY", "머리부터 발끝까지 전신이 모두 보이며 잘리지 않게 한다.",
            "THREE_QUARTER_BODY", "머리부터 무릎 아래까지 보이게 한다.",
            "HALF_BODY", "머리부터 허리 또는 골반 위까지 보이게 한다.",
            "CLOSE_UP", "지정된 상품 디테일이 화면 중심에 크게 보이게 한다.",
            "WIDE_MARGIN", "피사체 전체를 중앙에 배치하고 주변에 충분한 여백을 확보한다."
    );
    private static final Map<String, String> ASPECT_RATIO_LABELS = Map.of(
            "RATIO_1_1", "1:1",
            "RATIO_2_3", "2:3",
            "RATIO_3_4", "3:4",
            "RATIO_4_5", "4:5",
            "RATIO_16_9", "16:9",
            "RATIO_9_16", "9:16"
    );
    private static final Map<String, String> FINAL_CAMERA_INSTRUCTIONS = Map.of(
            "EYE_LEVEL_FRONT", """
                    The camera must be directly in front of the stationary subject at eye level.
                    Show the face, the front of the body, and the front of the garment.
                    Do not generate a three-quarter, side, or rear view.
                    """.strip(),
            "LEFT_THREE_QUARTER_45", """
                    Keep the subject stationary and move only the camera to 45 degrees left of the subject's anatomical front.
                    Show the garment front together with the subject's left side.
                    Do not turn the subject to face the camera.
                    """.strip(),
            "RIGHT_THREE_QUARTER_45", """
                    Keep the subject stationary and move only the camera to 45 degrees right of the subject's anatomical front.
                    Show the garment front together with the subject's right side.
                    Do not turn the subject to face the camera.
                    """.strip(),
            "BACK_VIEW", """
                    The camera must be directly behind the stationary subject.
                    Show the back of the head, the back of the body, and the rear side of the garment.
                    The face, chest, and garment front must not be visible.
                    The subject must not turn toward the camera or look over the shoulder.
                    Treat an attached PRODUCT_BACK reference as the highest-accuracy garment reference for rear details.
                    """.strip()
    );
    private static final Map<String, String> FINAL_FRAMING_INSTRUCTIONS = Map.of(
            "FULL_BODY", "Show the complete body from the top of the head to the feet without cropping.",
            "THREE_QUARTER_BODY", "Show the subject from the top of the head to below the knees.",
            "HALF_BODY", "Show the subject from the top of the head to the waist or upper pelvis.",
            "CLOSE_UP", "Use a tight close-up centered on the requested garment detail.",
            "WIDE_MARGIN", "Show the complete subject with generous surrounding negative space."
    );

    public ImageGenerationJobRespDto createJob(ImageGenerationJobCreateReqDto request, String loginId) {
        if (!gcsSaveEnabled) {
            throw new IllegalStateException("비동기 이미지 생성은 GCS 자동 저장이 필요합니다.");
        }

        User user = usersRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Brand brand = user.getBrand();
        Product product = productRepository.findByIdAndBrand_Id(request.getProductId(), brand.getId())
                .orElseThrow(() -> new IllegalArgumentException("상품이 존재하지 않거나 접근 권한이 없습니다."));
        BrandMood brandMood = findActiveBrandMood(brand.getId());
        GenerationPlan generationPlan = buildGenerationPlan(request, brandMood, brand.getId());

        ImageGenerationJob job = savePendingJob(
                request,
                user,
                brand,
                product,
                brandMood,
                generationPlan.storedPrompt(),
                generationPlan.requestedCount(),
                generationPlan.multiShot()
        );
        ImageGenerationWork work = toWork(
                job,
                product.getId(),
                generationPlan,
                System.currentTimeMillis()
        );
        imageGenerationWorker.submit(work);

        return ImageGenerationJobRespDto.from(job, List.of());
    }

    @Transactional(readOnly = true)
    public ImageGenerationJobRespDto getJob(Long jobId, String loginId) {
        User user = usersRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        ImageGenerationJob job = jobRepository.findByIdAndBrand_Id(jobId, user.getBrand().getId())
                .orElseThrow(ImageGenerationJobNotFoundException::new);

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
            BrandMood brandMood,
            String compiledPrompt,
            int requestedCount,
            boolean multiShot
    ) {
        ImageGenerationJob job = ImageGenerationJob.create(
                brand,
                product,
                brandMood.getId(),
                user,
                request.getCutType(),
                multiShot ? "PARALLEL" : normalizeGenerationMode(request.getGenerationMode()),
                compiledPrompt,
                request.getNegativePrompt(),
                normalizeJson(request.getUserOptionsJson()),
                normalizeJson(request.getReferenceJson()),
                googleAiImageProvider.getImageModel(),
                requestedCount
        );

        return jobRepository.save(job);
    }

    private ImageGenerationWork toWork(
            ImageGenerationJob job,
            Long productId,
            GenerationPlan generationPlan,
            long startedAt
    ) {
        JsonNode storedUserOptions = readStoredJson(job.getUserOptionsJson());
        String cameraAngle = optionValue(storedUserOptions, "cameraAngle");
        String framing = optionValue(storedUserOptions, "framing");
        String finalInstruction = buildFinalOutputInstruction(
                job.getCutType(),
                cameraAngle,
                framing,
                generationPlan.aspectRatio()
        );
        List<ImageGenerationWork.Task> tasks = IntStream.range(0, generationPlan.tasks().size())
                .mapToObj(outputIndex -> {
                    GenerationTask task = generationPlan.tasks().get(outputIndex);
                    GeneratedAssetMetadata metadata = buildGeneratedAssetMetadata(job, task.shotReferenceId());
                    return new ImageGenerationWork.Task(
                            outputIndex,
                            task.prompt(),
                            toProviderReferenceImages(task.referenceAssets()),
                            generationPlan.aspectRatio(),
                            cameraAngle,
                            framing,
                            finalInstruction,
                            metadata.assetRole(),
                            metadata.description()
                    );
                })
                .toList();

        return new ImageGenerationWork(job.getId(), productId, startedAt, tasks);
    }

    private BrandMood findActiveBrandMood(Long brandId) {
        return brandMoodRepository.findTopByBrandIdAndActiveTrueOrderByVersionDesc(brandId)
                .orElseThrow(() -> new IllegalArgumentException("활성화된 브랜드 무드가 없습니다. 브랜드 무드 분석 및 저장을 먼저 진행해주세요."));
    }

    private GenerationPlan buildGenerationPlan(
            ImageGenerationJobCreateReqDto request,
            BrandMood brandMood,
            Long brandId
    ) {
        String aspectRatio = resolveAspectRatio(request.getUserOptionsJson());
        JsonNode referenceRoot = parseReferenceJson(request.getReferenceJson());
        List<Long> shotReferenceIds = extractShotReferenceIds(referenceRoot, request.getCutType());
        Long singleShotReferenceId = extractSingleShotReferenceId(referenceRoot, request.getCutType());
        int requestedCount = resolveRequestedCount(request.getRequestedCount(), shotReferenceIds);
        List<ReferenceAssetInput> commonReferences = resolveReferenceAssets(referenceRoot, brandId);

        if (shotReferenceIds.isEmpty()) {
            ReferenceAsset shotReference = singleShotReferenceId == null
                    ? null
                    : findActiveReferenceAsset(singleShotReferenceId);
            List<ReferenceAssetInput> promptReferences = isMeasurementGuide(shotReference)
                    ? productAccuracyReferencesOnly(commonReferences)
                    : commonReferences;
            String prompt = isMeasurementGuide(shotReference)
                    ? compileMeasurementGuidePrompt(
                            request.getProductId(),
                            request.getPrompt(),
                            request.getUserOptionsJson(),
                            promptReferences,
                            shotReference
                    )
                    : compilePrompt(
                            brandMood,
                            request.getCutType(),
                            request.getPrompt(),
                            request.getUserOptionsJson(),
                            commonReferences
                    );
            List<GenerationTask> tasks = new ArrayList<>();
            for (int i = 0; i < requestedCount; i++) {
                tasks.add(new GenerationTask(prompt, promptReferences, null));
            }
            return new GenerationPlan(tasks, prompt, requestedCount, false, aspectRatio);
        }

        List<GenerationTask> tasks = new ArrayList<>();
        for (Long shotReferenceId : shotReferenceIds) {
            ReferenceAsset shotReference = findActiveReferenceAsset(shotReferenceId);
            List<ReferenceAssetInput> taskReferences = new ArrayList<>(commonReferences);
            if (!isMeasurementGuide(shotReference)) {
                addServiceReference(taskReferences, "shotReferenceImage", shotReferenceId);
            }
            validateReferenceCount(taskReferences);
            String prompt = isMeasurementGuide(shotReference)
                    ? compileMeasurementGuidePrompt(
                            request.getProductId(),
                            request.getPrompt(),
                            request.getUserOptionsJson(),
                            productAccuracyReferencesOnly(taskReferences),
                            shotReference
                    )
                    : compilePrompt(
                            brandMood,
                            request.getCutType(),
                            request.getPrompt(),
                            request.getUserOptionsJson(),
                            taskReferences
                    );
            tasks.add(new GenerationTask(prompt, List.copyOf(taskReferences), shotReferenceId));
        }

        return new GenerationPlan(tasks, buildStoredPrompt(tasks), requestedCount, true, aspectRatio);
    }

    private Long extractSingleShotReferenceId(JsonNode referenceRoot, String cutType) {
        if (referenceRoot == null || referenceRoot.isMissingNode() || referenceRoot.isNull()) {
            return null;
        }

        JsonNode singleNode = referenceRoot.path("shotReferenceId");
        if (singleNode.isMissingNode() || singleNode.isNull()) {
            return null;
        }

        if (!"PRODUCT_CUT".equals(normalizeValue(cutType))) {
            throw new IllegalArgumentException("shotReferenceId는 PRODUCT_CUT에서만 사용할 수 있습니다.");
        }
        if (!singleNode.isNumber()) {
            throw new IllegalArgumentException("shotReferenceId에는 숫자 ID만 입력할 수 있습니다.");
        }
        return singleNode.asLong();
    }

    private List<Long> extractShotReferenceIds(JsonNode referenceRoot, String cutType) {
        if (referenceRoot == null || referenceRoot.isMissingNode() || referenceRoot.isNull()) {
            return List.of();
        }

        JsonNode multipleNode = referenceRoot.path("shotReferenceIds");
        if (multipleNode.isMissingNode() || multipleNode.isNull()) {
            return List.of();
        }

        if (!"PRODUCT_CUT".equals(normalizeValue(cutType))) {
            throw new IllegalArgumentException("shotReferenceIds는 PRODUCT_CUT에서만 사용할 수 있습니다.");
        }
        if (!referenceRoot.path("shotReferenceId").isMissingNode()
                && !referenceRoot.path("shotReferenceId").isNull()) {
            throw new IllegalArgumentException("shotReferenceId와 shotReferenceIds는 동시에 사용할 수 없습니다.");
        }
        if (!multipleNode.isArray() || multipleNode.isEmpty()) {
            throw new IllegalArgumentException("shotReferenceIds는 1개 이상 4개 이하의 배열이어야 합니다.");
        }
        if (multipleNode.size() > 4) {
            throw new IllegalArgumentException("shotReferenceIds는 최대 4개까지 선택할 수 있습니다.");
        }

        List<Long> shotReferenceIds = new ArrayList<>();
        Set<Long> uniqueIds = new HashSet<>();
        for (JsonNode item : multipleNode) {
            if (!item.isNumber()) {
                throw new IllegalArgumentException("shotReferenceIds에는 숫자 ID만 입력할 수 있습니다.");
            }

            long shotReferenceId = item.asLong();
            if (!uniqueIds.add(shotReferenceId)) {
                throw new IllegalArgumentException("shotReferenceIds에 중복된 ID를 입력할 수 없습니다.");
            }
            shotReferenceIds.add(shotReferenceId);
        }

        return List.copyOf(shotReferenceIds);
    }

    private int resolveRequestedCount(Integer requestedCount, List<Long> shotReferenceIds) {
        if (shotReferenceIds != null && !shotReferenceIds.isEmpty()) {
            int shotCount = shotReferenceIds.size();
            if (requestedCount != null && requestedCount != shotCount) {
                throw new IllegalArgumentException("requestedCount는 shotReferenceIds 개수와 같아야 합니다.");
            }
            return shotCount;
        }

        int effectiveRequestedCount = requestedCount == null ? 2 : requestedCount;
        validateRequestedCount(effectiveRequestedCount);
        return effectiveRequestedCount;
    }

    private String buildStoredPrompt(List<GenerationTask> tasks) {
        StringBuilder storedPrompt = new StringBuilder();
        for (int i = 0; i < tasks.size(); i++) {
            GenerationTask task = tasks.get(i);
            if (i > 0) {
                storedPrompt.append("\n\n");
            }
            storedPrompt.append("[OUTPUT ")
                    .append(i + 1)
                    .append(" / SHOT_REFERENCE_ID ")
                    .append(task.shotReferenceId())
                    .append("]\n")
                    .append(task.prompt());
        }
        return storedPrompt.toString();
    }

    private String compilePrompt(
            BrandMood brandMood,
            String cutType,
            String userPrompt,
            JsonNode userOptions,
            List<ReferenceAssetInput> referenceAssets
    ) {
        String brandInstruction = brandMood.getBrandInstruction();
        if (brandInstruction == null || brandInstruction.isBlank()) {
            throw new IllegalArgumentException("브랜드 무드 지시문이 비어 있습니다.");
        }

        return """
                [SYSTEM INSTRUCTION]
                %s

                [BRAND INSTRUCTION]
                %s

                %s

                %s

                [IMAGE GENERATION REQUEST]
                %s
                """.formatted(
                buildSystemInstruction(cutType),
                brandInstruction.trim(),
                buildReferenceInstruction(cutType, referenceAssets),
                buildUserOptionsInstruction(cutType, userOptions),
                userPrompt.trim()
        );
    }

    private String compileMeasurementGuidePrompt(
            Long productId,
            String userPrompt,
            JsonNode userOptions,
            List<ReferenceAssetInput> referenceAssets,
            ReferenceAsset shotReference
    ) {
        String description = shotReference == null ? null : shotReference.getDescription();
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("실측가이드 템플릿 description이 비어 있습니다.");
        }

        String additionalRequest = userPrompt == null || userPrompt.isBlank() || "_".equals(userPrompt.trim())
                ? "No additional user request."
                : userPrompt.trim();

        return """
                [MEASUREMENT GUIDE PRODUCT IMAGE]
                Create one clean ecommerce measurement-guide product image.
                Use productImages only to understand the target product category, outline silhouette, length, proportions, collar, sleeves, hem, opening, seams, and construction.
                Do not copy product color, fabric color, pattern, logo, graphic, texture, lighting, shadow, or photographic style from productImages.

                %s

                [MEASUREMENT GUIDE DIRECTION]
                %s

                %s

                %s

                [OUTPUT CONDITIONS]
                The final result must be one complete product-only measurement-guide image.
                The final image must contain only:
                - a pure white solid background
                - thin black or dark gray garment outline line art
                - minimal internal construction and seam lines
                - dashed measurement guide lines
                - circular endpoint markers
                Keep the inside of the garment white and unfilled.
                Do not fill the garment with its real product color.
                Do not generate realistic fabric, texture, shading, gradient, shadow, dimensional depth, props, logo, pattern, text, numbers, measurement values, labels, captions, arrows, watermark, human, hand, body, mannequin, or hanger.

                [USER ADDITIONAL REQUEST]
                %s
                """.formatted(
                buildMeasurementGuideReferenceInstruction(referenceAssets),
                description.trim(),
                buildMeasurementPartsInstruction(productId),
                buildUserOptionsInstruction("PRODUCT_CUT", userOptions),
                additionalRequest
        );
    }

    private String buildMeasurementGuideReferenceInstruction(List<ReferenceAssetInput> referenceAssets) {
        StringBuilder keys = new StringBuilder();
        if (referenceAssets != null) {
            for (ReferenceAssetInput referenceAsset : referenceAssets) {
                keys.append("- ")
                        .append(referenceAsset.key())
                        .append(" = ")
                        .append(referenceAsset.sourceLabel())
                        .append("\n");
            }
        }

        String attachedKeys = keys.toString().trim();
        if (attachedKeys.isBlank()) {
            attachedKeys = "No product reference images are attached.";
        }

        return """
                [REFERENCE IMAGE ROLE RULES]
                The attached productImages are provided with explicit input keys.
                Interpret productImages only as product structure references.
                They define the final product category, outline silhouette, proportions, seams, and construction.
                They must not define the final color, texture, lighting, background, logo, label, pattern, or photographic style.
                Do not use any measurement-guide template image as an attached visual reference for this request.

                [ATTACHED REFERENCE IMAGE KEYS]
                %s
                """.formatted(attachedKeys);
    }

    private String buildMeasurementPartsInstruction(Long productId) {
        List<String> measurementNames = findMeasurementNames(productId);
        if (measurementNames.isEmpty()) {
            return """
                    [MEASUREMENT PARTS TO VISUALIZE]
                    No product-specific measurement parts are available in the database.
                    Use only the measurement guide direction above.
                    """;
        }

        String parts = measurementNames.stream()
                .map(this::toMeasurementGuideLineInstruction)
                .distinct()
                .map(instruction -> "- " + instruction)
                .toList()
                .stream()
                .collect(java.util.stream.Collectors.joining("\n"));

        return """
                [INTERNAL MEASUREMENT LINE CONTROL DATA - DO NOT RENDER]
                Use only the following visual guide line types:
                %s

                These guide line types are internal control data only.
                Draw only dashed guide lines and circular endpoint markers at the matching garment positions.
                Do not render these guide type names as text.
                Do not render Korean text, English text, measurement names, labels, numbers, size values, captions, callouts, or arrows.
                """.formatted(parts);
    }

    private String toMeasurementGuideLineInstruction(String measurementName) {
        return switch (measurementName) {
            case "어깨너비" -> "horizontal shoulder-width guide line across the shoulder endpoints";
            case "총장" -> "vertical body-length guide line from the top shoulder or neckline area to the bottom hem";
            case "소매길이" -> "sleeve-length guide line along the sleeve direction from shoulder endpoint to sleeve hem";
            case "가슴단면" -> "horizontal chest-width guide line across the underarm chest area";
            case "밑단단면", "밑단" -> "horizontal hem-width guide line across the bottom hem";
            case "허리단면", "허리" -> "horizontal waist-width guide line across the waist area";
            case "엉덩이단면", "힙단면", "힙" -> "horizontal hip-width guide line across the hip area";
            case "허벅지단면", "허벅지" -> "horizontal thigh-width guide line across the thigh area";
            case "밑위" -> "vertical rise guide line around the crotch and waist area";
            case "총기장", "바지총장" -> "vertical outseam-length guide line from waist to leg hem";
            default -> "appropriate dashed measurement guide line for one visible garment dimension";
        };
    }

    private List<String> findMeasurementNames(Long productId) {
        if (productId == null) {
            return List.of();
        }

        List<Size> sizes = sizeRepository.findByProductIdOrderByIdAsc(productId);
        if (sizes.isEmpty()) {
            return List.of();
        }

        List<Long> sizeIds = sizes.stream()
                .map(Size::getId)
                .toList();

        return sizeMeasurementRepository.findBySizeIdInOrderByIdAsc(sizeIds).stream()
                .map(SizeMeasurement::getName)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String buildUserOptionsInstruction(String cutType, JsonNode userOptions) {
        String colorTemperature = optionLabel(
                userOptions,
                "colorTemperature",
                COLOR_TEMPERATURE_LABELS
        );
        String cameraAngle = optionValue(userOptions, "cameraAngle");
        String framing = optionValue(userOptions, "framing");
        String aspectRatio = resolveAspectRatio(userOptions);

        List<String> instructions = new ArrayList<>();
        if (colorTemperature != null) {
            instructions.add("- 전체 이미지의 색온도는 " + colorTemperature + " 기반으로 유지한다.");
        }

        if (!"PRODUCT_CUT".equals(normalizeValue(cutType))) {
            if (cameraAngle != null) {
                String cameraAngleInstruction = CAMERA_ANGLE_INSTRUCTIONS.get(cameraAngle);
                if (cameraAngleInstruction != null) {
                    instructions.add("- " + cameraAngleInstruction.replace("\n", "\n- "));
                }
            }
            if (framing != null) {
                String framingInstruction = FRAMING_INSTRUCTIONS.get(framing);
                if (framingInstruction != null) {
                    instructions.add("- " + framingInstruction);
                }
            }
            instructions.add("- 피사체의 중심을 화면 가로·세로 중앙에 고정한다.");
            instructions.add("- 포즈 레퍼런스의 카메라 방향과 크롭이 사용자 선택값과 충돌하면 cameraAngle과 framing을 우선한다.");
        }

        if (aspectRatio != null) {
            instructions.add(
                    "- 최종 출력 이미지의 화면 비율은 반드시 "
                            + aspectRatio
                            + "로 생성한다. 다른 화면 비율로 생성하지 않는다."
            );
        }

        if (instructions.isEmpty()) {
            return "[USER SELECTED OPTIONS]\nNo additional user options were selected.";
        }

        return "[USER SELECTED OPTIONS]\n" + String.join("\n", instructions);
    }

    private String buildFinalOutputInstruction(
            String cutType,
            String cameraAngle,
            String framing,
            String aspectRatio
    ) {
        List<String> sections = new ArrayList<>();
        sections.add("[FINAL NON-NEGOTIABLE OUTPUT CONSTRAINTS]");

        if (!"PRODUCT_CUT".equals(normalizeValue(cutType))) {
            String cameraInstruction = cameraAngle == null
                    ? null
                    : FINAL_CAMERA_INSTRUCTIONS.get(cameraAngle);
            sections.add("""
                    PERSON IDENTITY:
                    MODEL_REFERENCE identity is a non-negotiable constraint.
                    The final person must be the same exact person as MODEL_REFERENCE.
                    Do not generate a different person.
                    Do not change race, ethnicity, face shape, skin tone, age impression, hairstyle, hair color, or body proportions.
                    If product, pose, background, brand mood, or user prompt conflicts with MODEL_REFERENCE identity, preserve MODEL_REFERENCE identity first.
                    """.strip());
            if (cameraInstruction != null) {
                sections.add("CAMERA VIEW:\n" + cameraInstruction);
            }
            sections.add("""
                    POSE:
                    Preserve only the relative limb, joint, torso, and head-to-torso arrangement from POSE_REFERENCE.
                    Keep the head naturally aligned with the torso.
                    Ignore the camera viewpoint visible in POSE_REFERENCE.
                    """.strip());
        }

        String framingInstruction = framing == null
                ? null
                : FINAL_FRAMING_INSTRUCTIONS.get(framing);
        if (framingInstruction != null && !"PRODUCT_CUT".equals(normalizeValue(cutType))) {
            sections.add("FRAMING:\n" + framingInstruction + "\nKeep the subject centered horizontally and vertically.");
        }

        if (aspectRatio != null) {
            sections.add("OUTPUT CANVAS:\n" + buildAspectRatioInstruction(aspectRatio));
        }

        sections.add("If any visual reference conflicts with these constraints, these final constraints take priority.");
        return String.join("\n\n", sections);
    }

    private String buildAspectRatioInstruction(String aspectRatio) {
        return switch (aspectRatio) {
            case "9:16" -> "Generate a vertical portrait image with an exact 9:16 aspect ratio. Do not generate a horizontal or 16:9 image.";
            case "2:3", "3:4", "4:5" -> "Generate a vertical portrait image with an exact " + aspectRatio + " aspect ratio. Do not generate a horizontal image.";
            case "16:9" -> "Generate a horizontal landscape image with an exact 16:9 aspect ratio. Do not generate a vertical image.";
            case "1:1" -> "Generate a square image with an exact 1:1 aspect ratio. Do not generate a horizontal or vertical rectangle.";
            default -> "Generate the final image with an exact " + aspectRatio + " aspect ratio.";
        };
    }

    private String buildSystemInstruction(String cutType) {
        if ("PRODUCT_CUT".equals(normalizeValue(cutType))) {
            return """
                    You are a fashion ecommerce product image generation director.
                    Generate one premium product-only ecommerce image by combining the references according to their explicit input keys.

                    The key names are critical:

                    productImages = GARMENT_REFERENCE
                    shotReferenceImage = SHOT_COMPOSITION_REFERENCE
                    backgroundImage = BACKGROUND_REFERENCE

                    Never swap the role of each reference image.

                    GARMENT_REFERENCE is the only product identity and product accuracy reference.
                    SHOT_COMPOSITION_REFERENCE is only the composition, crop, placement, view, lighting, and presentation reference.
                    BACKGROUND_REFERENCE is only the spatial mood and atmosphere reference.

                    Never use the product shown inside SHOT_COMPOSITION_REFERENCE as the final product.
                    Never copy its design, category, color, logo, text, pattern, material, or decoration.
                    The final product must be the exact product from GARMENT_REFERENCE.

                    Do not generate a person, visible body, face, skin, or hand.
                    Do not create a collage, split screen, comparison image, multiple panels, text, labels, watermarks, or graphic overlays.
                    Do not place the reference images directly inside the final result.

                    Priority order:
                    1. GARMENT_REFERENCE product accuracy
                    2. SHOT_COMPOSITION_REFERENCE composition and presentation
                    3. BACKGROUND_REFERENCE spatial mood and atmosphere
                    4. Brand mood
                    5. User selected options
                    """;
        }

        return """
                You are a fashion ecommerce image generation director.
                Combine the reference images provided or selected by the user to generate one premium fashion model image.

                The attached reference images are identified by explicit input keys, not by visual order.
                The key names are critical:

                productImages = GARMENT_REFERENCE
                outfitItems = OUTFIT_PRODUCT_REFERENCES
                modelImage = MODEL_REFERENCE
                poseImage = POSE_REFERENCE
                backgroundImage = BACKGROUND_REFERENCE

                Never swap the role of each reference image.

                Each reference image has a different role:
                GARMENT_REFERENCE is the product accuracy reference for the garment the final model must wear.
                OUTFIT_PRODUCT_REFERENCES are product references selected for this generation request. Each item has its own request role and wearing part.
                MODEL_REFERENCE is the identity reference for the final person.
                POSE_REFERENCE defines the subject's body posture in the subject's local body coordinate system.
                It does not define the camera position or viewing direction.
                BACKGROUND_REFERENCE is the spatial mood and atmosphere reference for the final scene.

                The final result must be one complete fashion ecommerce image.
                Do not create a collage, split screen, comparison image, multiple panels, text, labels, watermarks, or graphic overlays.
                Do not place the reference images directly inside the final result.
                Only one model may appear in the final image.

                Priority order:
                1. GARMENT_REFERENCE product accuracy
                2. MODEL_REFERENCE identity consistency
                3. User selected cameraAngle
                4. User selected framing
                5. POSE_REFERENCE body pose structure
                6. BACKGROUND_REFERENCE spatial mood and atmosphere
                7. Brand mood
                8. Color temperature and additional user prompt

                User selected cameraAngle and framing are hard constraints.
                They always override camera direction, framing, crop range, and subject placement inferred from POSE_REFERENCE.

                POSE_REFERENCE and cameraAngle use different coordinate systems.
                POSE_REFERENCE describes how the subject's head, torso, arms, and legs are positioned relative to one another.
                cameraAngle describes where the camera is placed around the subject.
                Do not rotate the subject's head or body toward the camera merely because the camera position changes.
                Preserve the relative body posture from POSE_REFERENCE while moving the camera to the position specified by cameraAngle.
                """;
    }

    private String buildReferenceInstruction(String cutType, List<ReferenceAssetInput> referenceAssets) {
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

        if ("PRODUCT_CUT".equals(normalizeValue(cutType))) {
            return buildProductCutReferenceInstruction(keys.toString().trim());
        }

        return """
                [REFERENCE IMAGE ROLE RULES]
                The attached reference images are provided with explicit input keys.
                Interpret each reference image only according to its input key.
                Never swap the role of each reference image.

                productImages = GARMENT_REFERENCE.
                productImages are the product accuracy references. The final model must wear the exact garment from productImages.
                Preserve the actual color, shape, fit, silhouette, length, material, logo position, washed fabric texture, drape, and construction details.
                Do not redesign the garment.
                Do not change the product category.
                Do not change the product color.
                Do not add pockets, zippers, buttons, labels, graphics, patterns, decorations, or logos that are not visible in productImages.

                outfitItems = OUTFIT_PRODUCT_REFERENCES.
                outfitItems are products selected for this generation request.
                A product can be PRIMARY in one request and STYLING in another request. Do not treat styling role as a fixed product property.
                Each outfit item includes a request role and wearing part.
                PRIMARY items are the most important products to preserve accurately in the final outfit.
                STYLING items are additional owned-brand products that should be naturally worn together with the PRIMARY item.
                Apply each outfit item only to its wearing part such as TOP, BOTTOM, OUTER, DRESS, SHOES, BAG, HAT, or ACCESSORY.
                Do not merge one outfit item into the wrong body part.
                Do not invent a different product category for an outfit item.

                modelImage = MODEL_REFERENCE.
                modelImage is the identity reference. The final person should look like the same person as modelImage.
                Preserve face shape, skin tone, body proportion, hair impression, age impression, and overall human mood.
                The garment and product details from productImages always override clothing in modelImage.
                If productImages provide only part of the outfit, use modelImage only for missing styling elements such as pants, shoes, accessories, socks, hats, or layering.
                Do not copy the entire existing outfit from modelImage when it conflicts with productImages.
                The final outfit should naturally combine the product reference item from productImages with the styling context from modelImage.

                poseImage = POSE_REFERENCE.
                POSE_REFERENCE defines posture in the subject's local body coordinate system, not in the camera coordinate system.
                Preserve arm and hand positions, elbow angles, leg and foot positions, knee angles, shoulder and pelvis angles, torso tilt and rotation, weight shift, head-to-torso alignment, gaze-to-torso alignment, and the overall body silhouette from poseImage.
                Preserve the head-to-torso and gaze-to-torso alignment shown in poseImage.
                The subject's anatomical forward direction is not the same as the camera viewing direction.
                Do not derive camera position, viewing direction, aspect ratio, crop range, subject position, or subject scale from poseImage.
                Do not copy poseImage identity, clothing, background, color tone, or brand mood.
                Move only the camera to the user selected cameraAngle while preserving the subject's relative posture.
                Do not rotate the subject's head, torso, or limbs toward the camera merely because the camera moves.
                Always keep the final subject centered horizontally and vertically in the frame.

                backgroundImage = BACKGROUND_REFERENCE.
                backgroundImage is the background mood reference. Use backgroundImage for background color, lighting feeling, spatial depth, material mood, and overall atmosphere. Do not copy people, text, logos, signs, or unnecessary objects from backgroundImage.

                [OUTPUT CONDITIONS]
                The final result must be one complete premium fashion ecommerce image.
                Only one model may appear in the final image.
                Do not create a collage, split screen, comparison image, multiple panels, or multiple-result layout.
                Do not place the reference images directly inside the final result.
                Do not add text, labels, watermarks, or graphic overlays.

                [ATTACHED REFERENCE IMAGE KEYS]
                %s
                """.formatted(keys.toString().trim());
    }

    private String buildProductCutReferenceInstruction(String attachedReferenceKeys) {
        return """
                [REFERENCE IMAGE ROLE RULES]
                The attached reference images are provided with explicit input keys.
                Interpret each reference image only according to its input key.
                Never swap the role of each reference image.

                productImages = GARMENT_REFERENCE.
                productImages are the final product identity and product accuracy references.
                Preserve the actual product color, shape, silhouette, proportion, material, logo position, texture, seams, and construction details.
                Do not redesign the product or change its category.
                Do not add pockets, zippers, buttons, labels, graphics, patterns, decorations, or logos that are not visible in productImages.

                shotReferenceImage = SHOT_COMPOSITION_REFERENCE.
                Use shotReferenceImage only for the requested product view, camera composition, crop level, product placement, lighting direction, background presentation, and detail emphasis.
                The product visible inside shotReferenceImage is not the target product.
                Never copy or preserve its product design, category, color, logo, text, pattern, material, or decoration.
                Replace it completely with the exact product from productImages while retaining only the shot presentation.

                backgroundImage = BACKGROUND_REFERENCE.
                Use backgroundImage only for background color, lighting feeling, spatial depth, material mood, and atmosphere.
                Do not copy products, people, text, logos, signs, or unnecessary objects from backgroundImage.

                [OUTPUT CONDITIONS]
                The final result must be one complete premium ecommerce product image.
                The product from productImages must be the only central product identity.
                Do not generate a person, visible body, face, skin, or hand.
                Do not create a collage, split screen, comparison image, multiple panels, or multiple-result layout.
                Do not place the reference images directly inside the final result.
                Do not add text, labels, watermarks, or graphic overlays.

                [ATTACHED REFERENCE IMAGE KEYS]
                %s
                """.formatted(attachedReferenceKeys);
    }

    private List<ReferenceAssetInput> resolveReferenceAssets(JsonNode referenceJson, Long brandId) {
        JsonNode root = parseReferenceJson(referenceJson);
        if (root == null || root.isMissingNode() || root.isNull() || root.isEmpty()) {
            return List.of();
        }

        List<ReferenceAssetInput> references = new ArrayList<>();
        addOutfitItems(references, root.path("outfitItems"), brandId);
        addProductImages(references, root.path("productImages"), brandId);
        addSingleReference(references, "modelImage", root.path("modelImage"), brandId);
        addSingleReference(references, "poseImage", root.path("poseImage"), brandId);
        addSingleReference(references, "backgroundImage", root.path("backgroundImage"), brandId);
        addServiceReference(references, "modelImage", root.path("modelReferenceId"));
        addServiceReference(references, "poseImage", root.path("poseReferenceId"));
        addServiceReference(references, "backgroundImage", root.path("backgroundReferenceId"));
        addServiceReference(references, "shotReferenceImage", root.path("shotReferenceId"));

        validateReferenceCount(references);

        return references;
    }

    private JsonNode parseReferenceJson(JsonNode referenceJson) {
        if (referenceJson == null || referenceJson.isMissingNode() || referenceJson.isNull()) {
            return objectMapper.createObjectNode();
        }

        if (!referenceJson.isTextual()) {
            return referenceJson;
        }

        String jsonText = referenceJson.asText();
        if (jsonText == null || jsonText.isBlank()) {
            return objectMapper.createObjectNode();
        }

        try {
            return objectMapper.readTree(jsonText);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("referenceJson 형식이 올바르지 않습니다.", e);
        }
    }

    private void addProductImages(List<ReferenceAssetInput> references, JsonNode node, Long brandId) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }

        if (node.isArray()) {
            int index = 0;
            for (JsonNode item : node) {
                addReference(references, "productImages[" + index + "]", item, brandId);
                index++;
            }
            return;
        }

        addReference(references, "productImages[0]", node, brandId);
    }

    private void addSingleReference(List<ReferenceAssetInput> references, String key, JsonNode node, Long brandId) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }

        addReference(references, key, node, brandId);
    }

    private void addReference(List<ReferenceAssetInput> references, String key, JsonNode node, Long brandId) {
        Long assetId = extractAssetId(node);
        Asset asset = assetRepository.findByIdWithProductAndBrand(assetId)
                .orElseThrow(() -> new IllegalArgumentException("참조 이미지를 찾을 수 없습니다. assetId=" + assetId));

        if (asset.getProduct() != null && !asset.getProduct().getBrand().getId().equals(brandId)) {
            throw new IllegalArgumentException("참조 이미지가 로그인 사용자의 브랜드에 속하지 않습니다. assetId=" + assetId);
        }

        references.add(new ReferenceAssetInput(
                key,
                "assetId " + asset.getId(),
                resolveReferenceRole(key, asset.getAssetRole()),
                asset.getDescription(),
                gcsService.download(asset.getObjectName()),
                asset.getContentType()
        ));
    }

    private void addServiceReference(List<ReferenceAssetInput> references, String key, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }

        addServiceReference(references, key, extractAssetId(node));
    }

    private void addServiceReference(List<ReferenceAssetInput> references, String key, Long referenceAssetId) {
        ReferenceAsset referenceAsset = findActiveReferenceAsset(referenceAssetId);

        references.add(new ReferenceAssetInput(
                key,
                "referenceAssetId " + referenceAsset.getId() + " (" + referenceAsset.getReferenceKey() + ")",
                resolveReferenceRole(key, null),
                referenceAsset.getDescription(),
                readReferenceAssetBytes(referenceAsset),
                referenceAsset.getContentType()
        ));
    }

    private ReferenceAsset findActiveReferenceAsset(Long referenceAssetId) {
        ReferenceAsset referenceAsset = referenceAssetRepository.findById(referenceAssetId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "서비스 레퍼런스 이미지를 찾을 수 없습니다. referenceAssetId=" + referenceAssetId
                ));

        if (!Boolean.TRUE.equals(referenceAsset.getActive())) {
            throw new IllegalArgumentException("비활성 서비스 레퍼런스 이미지입니다. referenceAssetId=" + referenceAssetId);
        }
        return referenceAsset;
    }

    private boolean isMeasurementGuide(ReferenceAsset referenceAsset) {
        if (referenceAsset == null) {
            return false;
        }

        String referenceKey = normalizeValue(referenceAsset.getReferenceKey());
        return "REF_PRODUCT_TEMPLATE_MEASUREMENT_GUIDE".equals(referenceKey)
                || "REF_ACCESSORY_TEMPLATE_MEASUREMENT_GUIDE".equals(referenceKey)
                || "REF_SHOT_TEMPLATE_12".equals(referenceKey);
    }

    private List<ReferenceAssetInput> productAccuracyReferencesOnly(List<ReferenceAssetInput> references) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }

        return references.stream()
                .filter(reference -> reference.key().startsWith("productImages"))
                .toList();
    }

    private void validateReferenceCount(List<ReferenceAssetInput> references) {
        if (references.size() > 14) {
            throw new IllegalArgumentException("참조 이미지는 생성 요청당 최대 14장까지만 첨부할 수 있습니다.");
        }
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

    private void addOutfitItems(List<ReferenceAssetInput> references, JsonNode node, Long brandId) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }

        if (!node.isArray()) {
            throw new IllegalArgumentException("outfitItems는 배열 형식이어야 합니다.");
        }

        int index = 0;
        for (JsonNode item : node) {
            String role = normalizeOutfitItemValue(item.path("role").asText("STYLING"));
            String wearingPart = normalizeOutfitItemValue(item.path("wearingPart").asText("UNSPECIFIED"));
            Long productId = item.path("productId").isNumber() ? item.path("productId").asLong() : null;
            JsonNode assetIds = item.path("assetIds");

            if (assetIds == null || assetIds.isMissingNode() || assetIds.isNull()) {
                throw new IllegalArgumentException("outfitItems.assetIds를 입력해주세요.");
            }

            if (assetIds.isArray()) {
                int assetIndex = 0;
                for (JsonNode assetId : assetIds) {
                    addOutfitItemReference(references, index, assetIndex, role, wearingPart, productId, assetId, brandId);
                    assetIndex++;
                }
            } else {
                addOutfitItemReference(references, index, 0, role, wearingPart, productId, assetIds, brandId);
            }

            index++;
        }
    }

    private void addOutfitItemReference(
            List<ReferenceAssetInput> references,
            int itemIndex,
            int assetIndex,
            String role,
            String wearingPart,
            Long productId,
            JsonNode node,
            Long brandId
    ) {
        Long assetId = extractAssetId(node);
        Asset asset = assetRepository.findByIdWithProductAndBrand(assetId)
                .orElseThrow(() -> new IllegalArgumentException("착장 상품 이미지를 찾을 수 없습니다. assetId=" + assetId));

        if (asset.getProduct() == null || !asset.getProduct().getBrand().getId().equals(brandId)) {
            throw new IllegalArgumentException("착장 상품 이미지가 로그인 사용자의 브랜드에 속하지 않습니다. assetId=" + assetId);
        }

        if (productId != null && !asset.getProduct().getId().equals(productId)) {
            throw new IllegalArgumentException("착장 상품 이미지가 요청 productId와 일치하지 않습니다. assetId=" + assetId);
        }

        references.add(new ReferenceAssetInput(
                "outfitItems[" + itemIndex + "].assetIds[" + assetIndex + "]",
                "outfitItem role=" + role
                        + ", wearingPart=" + wearingPart
                        + ", productId=" + nullToText(productId)
                        + ", assetId " + asset.getId(),
                "OUTFIT_PRODUCT_REFERENCE_" + role + "_" + wearingPart,
                asset.getDescription(),
                gcsService.download(asset.getObjectName()),
                asset.getContentType()
        ));
    }

    private String normalizeOutfitItemValue(String value) {
        if (value == null || value.isBlank()) {
            return "UNSPECIFIED";
        }

        return value.trim().toUpperCase();
    }

    private String nullToText(Long value) {
        return value == null ? "UNSPECIFIED" : value.toString();
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
                        referenceAsset.role(),
                        referenceAsset.description(),
                        referenceAsset.bytes(),
                        referenceAsset.contentType()
                ))
                .toList();
    }

    private String resolveReferenceRole(String key, String assetRole) {
        if (key.startsWith("productImages")) {
            if ("PRODUCT_FRONT".equals(normalizeValue(assetRole))) {
                return "PRODUCT_FRONT";
            }
            if ("PRODUCT_BACK".equals(normalizeValue(assetRole))) {
                return "PRODUCT_BACK";
            }
            return "GARMENT_REFERENCE";
        }
        if ("modelImage".equals(key)) {
            return "MODEL_REFERENCE";
        }
        if ("poseImage".equals(key)) {
            return "POSE_REFERENCE";
        }
        if ("backgroundImage".equals(key)) {
            return "BACKGROUND_REFERENCE";
        }
        if ("shotReferenceImage".equals(key)) {
            return "SHOT_COMPOSITION_REFERENCE";
        }
        return "REFERENCE_IMAGE";
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

    private GeneratedAssetMetadata buildGeneratedAssetMetadata(ImageGenerationJob job, Long shotReferenceId) {
        JsonNode userOptions = readStoredJson(job.getUserOptionsJson());
        JsonNode references = readStoredJson(job.getReferenceJson());
        String normalizedCutType = normalizeValue(job.getCutType());

        if ("MODEL_CUT".equals(normalizedCutType)) {
            return buildModelCutMetadata(userOptions, references);
        }

        return buildProductCutMetadata(userOptions, references, shotReferenceId);
    }

    private GeneratedAssetMetadata buildModelCutMetadata(JsonNode userOptions, JsonNode references) {
        String framing = optionValue(userOptions, "framing");
        String cameraAngleValue = optionValue(userOptions, "cameraAngle");
        String cameraAngle = cameraAngleValue == null ? null : CAMERA_ANGLE_LABELS.get(cameraAngleValue);
        String framingLabel = framing == null ? null : FRAMING_LABELS.get(framing);
        String colorTemperature = optionLabel(userOptions, "colorTemperature", COLOR_TEMPERATURE_LABELS);
        String aspectRatio = optionLabel(userOptions, "aspectRatio", ASPECT_RATIO_LABELS);
        String poseDescription = referenceAssetDescription(references, "poseReferenceId");
        String backgroundDescription = referenceAssetDescription(references, "backgroundReferenceId");

        List<String> sentences = new ArrayList<>();
        sentences.add("모델 착장컷.");
        addSentence(sentences, labeledDescription("요청 카메라 각도", cameraAngle));
        addSentence(sentences, labeledDescription("요청 프레이밍", framingLabel));
        addSentence(sentences, labeledDescription("요청 포즈(피사체 신체 기준)", poseDescription));
        if (poseDescription != null && !poseDescription.isBlank()) {
            addSentence(sentences, "포즈 설명의 방향은 피사체 몸통 기준이며 카메라 촬영 방향과 무관하다");
        }
        addSentence(sentences, labeledDescription("요청 색온도", colorTemperature));
        addSentence(sentences, labeledDescription("요청 배경", backgroundDescription));
        addSentence(sentences, labeledDescription("요청 비율", aspectRatio));

        return new GeneratedAssetMetadata(
                buildModelAssetRole(framing, cameraAngleValue),
                String.join(" ", sentences)
        );
    }

    private String buildModelAssetRole(String framing, String cameraAngle) {
        String validFraming = framing != null && FRAMING_LABELS.containsKey(framing) ? framing : null;
        String angleSuffix = cameraAngle == null ? null : CAMERA_ANGLE_ROLE_SUFFIXES.get(cameraAngle);

        if (validFraming != null && angleSuffix != null) {
            return validFraming + "_" + angleSuffix;
        }
        if (validFraming != null) {
            return validFraming;
        }
        if (angleSuffix != null) {
            return "MODEL_" + angleSuffix;
        }
        return "GENERATED_IMAGE";
    }

    private GeneratedAssetMetadata buildProductCutMetadata(JsonNode userOptions, JsonNode references) {
        return buildProductCutMetadata(userOptions, references, null);
    }

    private GeneratedAssetMetadata buildProductCutMetadata(
            JsonNode userOptions,
            JsonNode references,
            Long shotReferenceId
    ) {
        String productView = optionValue(userOptions, "productView");
        java.util.Optional<ReferenceAsset> shotReference = shotReferenceId == null
                ? findReferenceAsset(references, "shotReferenceId")
                : referenceAssetRepository.findById(shotReferenceId);
        String shotReferenceLabel = shotReference.map(ReferenceAsset::getName).orElse(null);
        String shotReferenceKey = shotReference
                .map(ReferenceAsset::getReferenceKey)
                .map(this::normalizeValue)
                .orElse(null);
        String colorTemperature = optionLabel(userOptions, "colorTemperature", COLOR_TEMPERATURE_LABELS);
        String aspectRatio = optionLabel(userOptions, "aspectRatio", ASPECT_RATIO_LABELS);
        String normalizedProductView = normalizeValue(productView);

        List<String> sentences = new ArrayList<>();
        sentences.add("제품 단독컷.");
        addSentence(sentences, textWithSuffix(shotReferenceLabel, " 구도를 반영했다"));
        addSentence(sentences, textWithSuffix(colorTemperature, " 색감으로 생성되었다"));
        addSentence(sentences, textWithSuffix(aspectRatio, " 비율이다"));

        return new GeneratedAssetMetadata(
                normalizeProductAssetRole(
                        normalizedProductView != null ? normalizedProductView : shotReferenceKey
                ),
                String.join(" ", sentences)
        );
    }

    private String normalizeProductAssetRole(String shotReferenceKey) {
        if (shotReferenceKey == null) {
            return "PRODUCT_CUT";
        }

        return switch (shotReferenceKey) {
            case "FRONT" -> "FRONT_VIEW";
            case "BACK" -> "BACK_VIEW";
            case "SIDE" -> "SIDE_VIEW";
            case "DETAIL", "LOGO", "CONSTRUCTION" -> "DETAIL_CLOSEUP";
            case "FABRIC" -> "FABRIC_MACRO";
            case "REF_PRODUCT_TEMPLATE_FRONT", "REF_ACCESSORY_TEMPLATE_FRONT" -> "FRONT_VIEW";
            case "REF_PRODUCT_TEMPLATE_THREE_QUARTER", "REF_ACCESSORY_TEMPLATE_THREE_QUARTER" -> "THREE_QUARTER_VIEW";
            case "REF_PRODUCT_TEMPLATE_SIDE", "REF_ACCESSORY_TEMPLATE_SIDE" -> "SIDE_VIEW";
            case "REF_PRODUCT_TEMPLATE_BACK", "REF_ACCESSORY_TEMPLATE_BACK" -> "BACK_VIEW";
            case "REF_PRODUCT_TEMPLATE_FLAT_LAY" -> "FLAT_LAY";
            case "REF_PRODUCT_TEMPLATE_GHOST_MANNEQUIN" -> "GHOST_MANNEQUIN";
            case "REF_PRODUCT_TEMPLATE_FOLDING" -> "FOLDING";
            case "REF_PRODUCT_TEMPLATE_HANGING" -> "HANGING";
            case "REF_PRODUCT_TEMPLATE_FABRIC_MACRO", "REF_ACCESSORY_TEMPLATE_FABRIC_MACRO" -> "FABRIC_MACRO";
            case "REF_PRODUCT_TEMPLATE_LOGO_DETAIL", "REF_ACCESSORY_TEMPLATE_LOGO_DETAIL" -> "LOGO_DETAIL";
            case "REF_PRODUCT_TEMPLATE_STITCH_DETAIL", "REF_ACCESSORY_TEMPLATE_STITCH_DETAIL" -> "STITCH_DETAIL";
            case "REF_PRODUCT_TEMPLATE_MEASUREMENT_GUIDE",
                 "REF_ACCESSORY_TEMPLATE_MEASUREMENT_GUIDE",
                 "REF_SHOT_TEMPLATE_12" -> "MEASUREMENT_GUIDE";
            default -> shotReferenceKey;
        };
    }

    private JsonNode readStoredJson(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return objectMapper.createObjectNode();
        }

        try {
            return objectMapper.readTree(jsonText);
        } catch (JacksonException e) {
            return objectMapper.createObjectNode();
        }
    }

    private String optionLabel(JsonNode options, String key, Map<String, String> labels) {
        String value = optionValue(options, key);
        return value == null ? null : labels.get(value);
    }

    private String resolveAspectRatio(JsonNode userOptions) {
        String aspectRatioValue = optionValue(userOptions, "aspectRatio");
        if (aspectRatioValue == null || aspectRatioValue.isBlank()) {
            throw new IllegalArgumentException("이미지 비율은 필수입니다.");
        }

        String aspectRatio = ASPECT_RATIO_LABELS.get(aspectRatioValue);
        if (aspectRatio == null) {
            throw new IllegalArgumentException("지원하지 않는 이미지 비율입니다: " + aspectRatioValue);
        }
        return aspectRatio;
    }

    private String optionValue(JsonNode options, String key) {
        if (options == null || options.isMissingNode() || options.isNull()) {
            return null;
        }

        return normalizeValue(options.path(key).asText(null));
    }

    private String referenceAssetDescription(JsonNode references, String key) {
        return findReferenceAsset(references, key)
                .map(ReferenceAsset::getDescription)
                .filter(description -> !description.isBlank())
                .orElse(null);
    }

    private String referenceAssetKey(JsonNode references, String key) {
        return findReferenceAsset(references, key)
                .map(ReferenceAsset::getReferenceKey)
                .map(this::normalizeValue)
                .orElse(null);
    }

    private java.util.Optional<ReferenceAsset> findReferenceAsset(JsonNode references, String key) {
        if (references == null || references.isMissingNode() || references.isNull()) {
            return java.util.Optional.empty();
        }

        JsonNode referenceIdNode = references.path(key);
        if (!referenceIdNode.isNumber()) {
            return java.util.Optional.empty();
        }

        return referenceAssetRepository.findById(referenceIdNode.asLong());
    }

    private String normalizeValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim().toUpperCase();
    }

    private String textWithSuffix(String value, String suffix) {
        return value == null ? null : value + suffix;
    }

    private String labeledDescription(String label, String description) {
        if (description == null || description.isBlank()) {
            return null;
        }

        String normalizedDescription = description.trim();
        while (normalizedDescription.endsWith(".")) {
            normalizedDescription = normalizedDescription.substring(0, normalizedDescription.length() - 1).trim();
        }

        return normalizedDescription.isBlank() ? null : label + ": " + normalizedDescription;
    }

    private void addSentence(List<String> sentences, String sentence) {
        if (sentence != null && !sentence.isBlank()) {
            sentences.add(sentence + ".");
        }
    }

    private String normalizeJson(JsonNode json) {
        if (json == null || json.isMissingNode() || json.isNull()) {
            return "{}";
        }

        if (json.isTextual()) {
            String jsonText = json.asText();
            if (jsonText == null || jsonText.isBlank()) {
                return "{}";
            }

            return jsonText;
        }

        try {
            return objectMapper.writeValueAsString(json);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("JSON 형식이 올바르지 않습니다.", e);
        }
    }

    private record ReferenceAssetInput(
            String key,
            String sourceLabel,
            String role,
            String description,
            byte[] bytes,
            String contentType
    ) {
    }

    private record GenerationTask(
            String prompt,
            List<ReferenceAssetInput> referenceAssets,
            Long shotReferenceId
    ) {
    }

    private record GenerationPlan(
            List<GenerationTask> tasks,
            String storedPrompt,
            int requestedCount,
            boolean multiShot,
            String aspectRatio
    ) {
    }

    private record GeneratedAssetMetadata(
            String assetRole,
            String description
    ) {
    }
}
