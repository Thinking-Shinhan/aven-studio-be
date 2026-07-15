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
import kr.co.daeun.avenstudiobe.domain.product.repository.ProductRepository;
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
    private static final Map<String, String> FRAMING_LABELS = Map.of(
            "FULL_BODY", "전신",
            "THREE_QUARTER_BODY", "3/4 전신",
            "HALF_BODY", "반신",
            "CLOSE_UP", "클로즈업",
            "WIDE_MARGIN", "여백 강조"
    );
    private static final Map<String, String> ASPECT_RATIO_LABELS = Map.of(
            "RATIO_1_1", "1:1",
            "RATIO_2_3", "2:3",
            "RATIO_3_4", "3:4",
            "RATIO_4_5", "4:5",
            "RATIO_16_9", "16:9",
            "RATIO_9_16", "9:16"
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
        List<ImageGenerationWork.Task> tasks = IntStream.range(0, generationPlan.tasks().size())
                .mapToObj(outputIndex -> {
                    GenerationTask task = generationPlan.tasks().get(outputIndex);
                    GeneratedAssetMetadata metadata = buildGeneratedAssetMetadata(job, task.shotReferenceId());
                    return new ImageGenerationWork.Task(
                            outputIndex,
                            task.prompt(),
                            toProviderReferenceImages(task.referenceAssets()),
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
        JsonNode referenceRoot = parseReferenceJson(request.getReferenceJson());
        List<Long> shotReferenceIds = extractShotReferenceIds(referenceRoot, request.getCutType());
        int requestedCount = resolveRequestedCount(request.getRequestedCount(), shotReferenceIds);
        List<ReferenceAssetInput> commonReferences = resolveReferenceAssets(referenceRoot, brandId);

        if (shotReferenceIds.isEmpty()) {
            String prompt = compilePrompt(
                    brandMood,
                    request.getCutType(),
                    request.getPrompt(),
                    request.getUserOptionsJson(),
                    commonReferences
            );
            List<GenerationTask> tasks = new ArrayList<>();
            for (int i = 0; i < requestedCount; i++) {
                tasks.add(new GenerationTask(prompt, commonReferences, null));
            }
            return new GenerationPlan(tasks, prompt, requestedCount, false);
        }

        List<GenerationTask> tasks = new ArrayList<>();
        for (Long shotReferenceId : shotReferenceIds) {
            List<ReferenceAssetInput> taskReferences = new ArrayList<>(commonReferences);
            addServiceReference(taskReferences, "shotReferenceImage", shotReferenceId);
            validateReferenceCount(taskReferences);
            String prompt = compilePrompt(
                    brandMood,
                    request.getCutType(),
                    request.getPrompt(),
                    request.getUserOptionsJson(),
                    taskReferences
            );
            tasks.add(new GenerationTask(prompt, List.copyOf(taskReferences), shotReferenceId));
        }

        return new GenerationPlan(tasks, buildStoredPrompt(tasks), requestedCount, true);
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

    private String buildUserOptionsInstruction(String cutType, JsonNode userOptions) {
        String colorTemperature = optionLabel(
                userOptions,
                "colorTemperature",
                COLOR_TEMPERATURE_LABELS
        );
        String cameraAngle = optionLabel(
                userOptions,
                "cameraAngle",
                CAMERA_ANGLE_LABELS
        );
        String framing = optionLabel(
                userOptions,
                "framing",
                FRAMING_LABELS
        );
        String aspectRatio = optionLabel(
                userOptions,
                "aspectRatio",
                ASPECT_RATIO_LABELS
        );

        List<String> instructions = new ArrayList<>();
        if (colorTemperature != null) {
            instructions.add("- 전체 이미지의 색온도는 " + colorTemperature + " 기반으로 유지한다.");
        }

        if (!"PRODUCT_CUT".equals(normalizeValue(cutType))) {
            if (cameraAngle != null) {
                instructions.add("- 카메라 각도는 " + cameraAngle + "으로 구성한다.");
            }
            if (framing != null) {
                instructions.add("- 구도는 " + framing + " 구도로 구성한다.");
            }
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
                poseImage = POSE_COMPOSITION_REFERENCE
                backgroundImage = BACKGROUND_REFERENCE

                Never swap the role of each reference image.

                Each reference image has a different role:
                GARMENT_REFERENCE is the product accuracy reference for the garment the final model must wear.
                OUTFIT_PRODUCT_REFERENCES are product references selected for this generation request. Each item has its own request role and wearing part.
                MODEL_REFERENCE is the identity reference for the final person.
                POSE_COMPOSITION_REFERENCE is the body pose and camera composition reference.
                BACKGROUND_REFERENCE is the spatial mood and atmosphere reference for the final scene.

                The final result must be one complete fashion ecommerce image.
                Do not create a collage, split screen, comparison image, multiple panels, text, labels, watermarks, or graphic overlays.
                Do not place the reference images directly inside the final result.
                Only one model may appear in the final image.

                Priority order:
                1. GARMENT_REFERENCE product accuracy
                2. PRIMARY outfit item accuracy
                3. STYLING outfit item consistency
                4. MODEL_REFERENCE identity consistency
                5. POSE_COMPOSITION_REFERENCE body pose structure
                6. BACKGROUND_REFERENCE spatial mood and atmosphere
                7. Brand mood
                8. User selected mood options
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

                poseImage = POSE_COMPOSITION_REFERENCE.
                poseImage is not a mood reference. It is the exact body pose and camera composition template.
                Reproduce the pose from poseImage as closely as possible.
                Do not reinterpret it as a generic ecommerce pose.
                Do not casually change arm, hand, leg, foot, shoulder, pelvis, waist, neck, head, or gaze direction.
                Match body direction, upper body rotation, shoulder angle, pelvis angle, waist tilt, head direction, gaze direction, arm position, elbow angle, hand position, rough finger direction, leg position, knee angle, foot direction, weight shift, total silhouette, camera angle, crop range, and model position in frame as closely as possible.
                Use poseImage only for body pose, body direction, camera angle, framing, crop range, and model placement inside the frame.
                Do not copy poseImage face, identity, clothing, gray 3D material, background, color tone, or brand mood.

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
                        referenceAsset.bytes(),
                        referenceAsset.contentType()
                ))
                .toList();
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
        String cameraAngle = optionLabel(userOptions, "cameraAngle", CAMERA_ANGLE_LABELS);
        String framingLabel = framing == null ? null : FRAMING_LABELS.get(framing);
        String colorTemperature = optionLabel(userOptions, "colorTemperature", COLOR_TEMPERATURE_LABELS);
        String aspectRatio = optionLabel(userOptions, "aspectRatio", ASPECT_RATIO_LABELS);
        String poseDescription = referenceAssetDescription(references, "poseReferenceId");
        String backgroundDescription = referenceAssetDescription(references, "backgroundReferenceId");

        List<String> sentences = new ArrayList<>();
        sentences.add("모델 착장컷.");
        addSentence(sentences, joinWithSpace(cameraAngle, framingLabel, "구도"));
        addSentence(sentences, labeledDescription("포즈", poseDescription));
        addSentence(sentences, textWithSuffix(colorTemperature, " 색감으로 생성되었다"));
        addSentence(sentences, labeledDescription("배경", backgroundDescription));
        addSentence(sentences, textWithSuffix(aspectRatio, " 비율이다"));

        return new GeneratedAssetMetadata(
                framing != null && FRAMING_LABELS.containsKey(framing) ? framing : "GENERATED_IMAGE",
                String.join(" ", sentences)
        );
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

    private String joinWithSpace(String first, String second, String suffix) {
        if (first == null && second == null) {
            return null;
        }

        String value = first == null ? second : second == null ? first : first + " " + second;
        return value + " " + suffix.trim();
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
            boolean multiShot
    ) {
    }

    private record GeneratedAssetMetadata(
            String assetRole,
            String description
    ) {
    }
}
