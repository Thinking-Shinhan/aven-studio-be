package kr.co.daeun.avenstudiobe.domain.imagegeneration.service;

import kr.co.daeun.avenstudiobe.domain.asset.repository.AssetRepository;
import kr.co.daeun.avenstudiobe.domain.brand.entity.Brand;
import kr.co.daeun.avenstudiobe.domain.brandmood.entity.BrandMood;
import kr.co.daeun.avenstudiobe.domain.brandmood.repository.BrandMoodRepository;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ImageGenerationJobCreateReqDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ImageGenerationJobRespDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.entity.ImageGenerationJob;
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
import kr.co.daeun.avenstudiobe.integration.google.GoogleAiImageProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageGenerationServiceTest {

    private final ImageGenerationService service = new ImageGenerationService(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
    );
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void missingAspectRatioIsRejected() {
        JsonNode userOptions = objectMapper.createObjectNode();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> invokeAspectRatio(userOptions)
        );

        assertEquals("이미지 비율은 필수입니다.", exception.getMessage());
    }

    @Test
    void omittedUserOptionsDoNotThrowWhenBuildingMetadata() throws Exception {
        JsonNode emptyJson = objectMapper.createObjectNode();

        assertDoesNotThrow(() -> invokeMetadataBuilder("buildModelCutMetadata", emptyJson));
        assertDoesNotThrow(() -> invokeMetadataBuilder("buildProductCutMetadata", emptyJson));
    }

    @Test
    void productCutInstructionTreatsShotReferenceAsCompositionOnly() throws Exception {
        String instruction = invokeSystemInstruction("PRODUCT_CUT");

        assertTrue(instruction.contains("shotReferenceImage = SHOT_COMPOSITION_REFERENCE"));
        assertTrue(instruction.contains("Never use the product shown inside SHOT_COMPOSITION_REFERENCE"));
        assertTrue(instruction.contains("The final product must be the exact product from GARMENT_REFERENCE"));
        assertFalse(instruction.contains("Only one model may appear"));
    }

    @Test
    void modelCutInstructionKeepsModelReferenceRules() throws Exception {
        String instruction = invokeSystemInstruction("MODEL_CUT");

        assertTrue(instruction.contains("modelImage = MODEL_REFERENCE"));
        assertTrue(instruction.contains("Only one model may appear"));
    }

    @Test
    void modelCutUserOptionsAreCompiledIntoGenerationInstruction() throws Exception {
        JsonNode userOptions = objectMapper.readTree("""
                {
                  "colorTemperature":"COOL",
                  "cameraAngle":"EYE_LEVEL_FRONT",
                  "framing":"THREE_QUARTER_BODY",
                  "aspectRatio":"RATIO_3_4"
                }
                """);

        String instruction = invokeUserOptionsInstruction("MODEL_CUT", userOptions);

        assertTrue(instruction.contains("색온도는 쿨톤 기반으로 유지한다"));
        assertTrue(instruction.contains("카메라를 모델의 신체 정면 앞에 배치한다"));
        assertTrue(instruction.contains("피사체가 사선이나 후면으로 회전하지 않도록 한다"));
        assertTrue(instruction.contains("머리부터 무릎 아래까지 보이게 한다"));
        assertTrue(instruction.contains("피사체의 중심을 화면 가로·세로 중앙에 고정한다"));
        assertTrue(instruction.contains("화면 비율은 반드시 3:4로 생성한다"));
    }

    @Test
    void allCameraAnglesUseExplicitHardConstraints() throws Exception {
        assertCameraInstructionContains(
                "EYE_LEVEL_FRONT",
                "카메라를 모델의 신체 정면 앞에 배치한다"
        );
        assertCameraInstructionContains(
                "LEFT_THREE_QUARTER_45",
                "카메라만 모델의 정면 기준 좌측 45도 위치로 이동한다"
        );
        assertCameraInstructionContains(
                "RIGHT_THREE_QUARTER_45",
                "카메라만 모델의 정면 기준 우측 45도 위치로 이동한다"
        );
        assertCameraInstructionContains(
                "BACK_VIEW",
                "카메라만 모델의 등 뒤 정중앙으로 이동한다"
        );
    }

    @Test
    void allFramingsUseExplicitCropAndCenterConstraints() throws Exception {
        assertFramingInstructionContains("FULL_BODY", "머리부터 발끝까지 전신이 모두 보이며 잘리지 않게 한다");
        assertFramingInstructionContains("THREE_QUARTER_BODY", "머리부터 무릎 아래까지 보이게 한다");
        assertFramingInstructionContains("HALF_BODY", "머리부터 허리 또는 골반 위까지 보이게 한다");
        assertFramingInstructionContains("CLOSE_UP", "지정된 상품 디테일이 화면 중심에 크게 보이게 한다");
        assertFramingInstructionContains("WIDE_MARGIN", "주변에 충분한 여백을 확보한다");
    }

    @Test
    void modelCutPrioritizesUserCompositionOverPoseComposition() throws Exception {
        String instruction = invokeSystemInstruction("MODEL_CUT");

        assertTrue(instruction.contains("3. User selected cameraAngle"));
        assertTrue(instruction.contains("4. User selected framing"));
        assertTrue(instruction.contains("5. POSE_REFERENCE body pose structure"));
        assertTrue(instruction.contains("always override camera direction, framing, crop range"));
        assertTrue(instruction.contains("POSE_REFERENCE and cameraAngle use different coordinate systems"));
        assertTrue(instruction.contains("cameraAngle describes where the camera is placed around the subject"));
        assertTrue(instruction.contains("Do not rotate the subject's head or body toward the camera"));
        assertFalse(instruction.contains("POSE_COMPOSITION_REFERENCE is the body pose and camera composition reference"));
    }

    @Test
    void backViewOverridesFrontFacingPoseReference() throws Exception {
        String userInstruction = invokeUserOptionsInstruction(
                "MODEL_CUT",
                objectMapper.readTree("""
                        {"cameraAngle":"BACK_VIEW","framing":"FULL_BODY"}
                        """)
        );
        String poseInstruction = invokeReferenceInstructionWithPose();

        assertTrue(userInstruction.contains("카메라만 모델의 등 뒤 정중앙으로 이동한다"));
        assertTrue(userInstruction.contains("모델의 뒷통수, 등, 상품 후면이 보여야 한다"));
        assertTrue(userInstruction.contains("얼굴, 가슴과 상품 앞면이 보이면 안 된다"));
        assertTrue(userInstruction.contains("어깨 너머로 바라보면 안 된다"));
        assertTrue(userInstruction.contains("포즈 레퍼런스의 팔, 다리, 어깨, 골반과 체중 이동은 그대로 유지한다"));
        assertTrue(poseInstruction.contains("Preserve the head-to-torso and gaze-to-torso alignment"));
        assertTrue(poseInstruction.contains("anatomical forward direction is not the same as the camera viewing direction"));
    }

    @Test
    void backViewFinalConstraintIsRepeatedAfterReferencesWithPortraitRatio() throws Exception {
        String instruction = invokeFinalOutputInstruction(
                "MODEL_CUT",
                "BACK_VIEW",
                "FULL_BODY",
                "9:16"
        );

        assertTrue(instruction.contains("FINAL NON-NEGOTIABLE OUTPUT CONSTRAINTS"));
        assertTrue(instruction.contains("camera must be directly behind the stationary subject"));
        assertTrue(instruction.contains("back of the head"));
        assertTrue(instruction.contains("face, chest, and garment front must not be visible"));
        assertTrue(instruction.contains("PRODUCT_BACK"));
        assertTrue(instruction.contains("complete body from the top of the head to the feet"));
        assertTrue(instruction.contains("vertical portrait image with an exact 9:16 aspect ratio"));
        assertTrue(instruction.contains("Do not generate a horizontal or 16:9 image"));
    }

    @Test
    void eyeLevelFrontOverridesBackFacingPoseReference() throws Exception {
        String userInstruction = invokeUserOptionsInstruction(
                "MODEL_CUT",
                objectMapper.readTree("""
                        {"cameraAngle":"EYE_LEVEL_FRONT","framing":"FULL_BODY"}
                        """)
        );
        String poseInstruction = invokeReferenceInstructionWithPose();

        assertTrue(userInstruction.contains("카메라를 모델의 신체 정면 앞에 배치한다"));
        assertTrue(poseInstruction.contains("Do not derive camera position, viewing direction"));
        assertTrue(poseInstruction.contains("Always keep the final subject centered"));
    }

    @Test
    void everyCameraAngleAndFramingCombinationKeepsCenteredSubject() throws Exception {
        List<String> cameraAngles = List.of(
                "EYE_LEVEL_FRONT",
                "LEFT_THREE_QUARTER_45",
                "RIGHT_THREE_QUARTER_45",
                "BACK_VIEW"
        );
        List<String> framings = List.of(
                "FULL_BODY",
                "THREE_QUARTER_BODY",
                "HALF_BODY",
                "CLOSE_UP",
                "WIDE_MARGIN"
        );

        for (String cameraAngle : cameraAngles) {
            for (String framing : framings) {
                String instruction = invokeUserOptionsInstruction(
                        "MODEL_CUT",
                        objectMapper.readTree("""
                                {"cameraAngle":"%s","framing":"%s"}
                                """.formatted(cameraAngle, framing))
                );

                assertTrue(instruction.contains("피사체의 중심을 화면 가로·세로 중앙에 고정한다"));
            }
        }
    }

    @Test
    void unsupportedAspectRatioIsRejectedBeforeGeneration() throws Throwable {
        JsonNode userOptions = objectMapper.readTree("""
                {"aspectRatio":"RATIO_5_7"}
                """);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> invokeAspectRatio(userOptions)
        );

        assertEquals("지원하지 않는 이미지 비율입니다: RATIO_5_7", exception.getMessage());
    }

    @Test
    void productCutUserOptionsExcludeModelOnlyOptions() throws Exception {
        JsonNode userOptions = objectMapper.readTree("""
                {
                  "colorTemperature":"NEUTRAL",
                  "cameraAngle":"BACK_VIEW",
                  "framing":"FULL_BODY",
                  "aspectRatio":"RATIO_1_1"
                }
                """);

        String instruction = invokeUserOptionsInstruction("PRODUCT_CUT", userOptions);

        assertTrue(instruction.contains("색온도는 뉴트럴 기반으로 유지한다"));
        assertTrue(instruction.contains("화면 비율은 반드시 1:1로 생성한다"));
        assertFalse(instruction.contains("카메라 각도"));
        assertFalse(instruction.contains("전신 구도"));
    }

    @Test
    void omittedModelCutOptionsStillEnforceCenteredSubject() throws Exception {
        String instruction = invokeUserOptionsInstruction("MODEL_CUT", null);

        assertTrue(instruction.contains("피사체의 중심을 화면 가로·세로 중앙에 고정한다"));
    }

    @Test
    void modelCutMetadataUsesReferenceDescriptionsInsteadOfRecommendationNames() throws Throwable {
        ReferenceAssetRepository referenceAssetRepository = mock(ReferenceAssetRepository.class);
        ReferenceAsset pose = mock(ReferenceAsset.class);
        ReferenceAsset background = mock(ReferenceAsset.class);
        when(pose.getName()).thenReturn("추천 포즈 1");
        when(pose.getDescription()).thenReturn("한쪽 다리를 앞으로 내딛고 상체를 자연스럽게 회전한 자세.");
        when(background.getName()).thenReturn("추천 배경 1");
        when(background.getDescription()).thenReturn("밝은 화이트 스튜디오와 부드러운 확산광으로 구성된 공간.");
        when(referenceAssetRepository.findById(11L)).thenReturn(Optional.of(pose));
        when(referenceAssetRepository.findById(3L)).thenReturn(Optional.of(background));

        ImageGenerationService metadataService = new ImageGenerationService(
                null,
                null,
                null,
                null,
                null,
                referenceAssetRepository,
                null,
                null,
                null,
                null,
                null,
                null
        );
        JsonNode userOptions = objectMapper.readTree("""
                {
                  "colorTemperature":"COOL",
                  "cameraAngle":"BACK_VIEW",
                  "framing":"FULL_BODY",
                  "aspectRatio":"RATIO_1_1"
                }
                """);
        JsonNode references = objectMapper.readTree("""
                {"poseReferenceId":11,"backgroundReferenceId":3}
                """);

        String description = invokeModelMetadataDescription(metadataService, userOptions, references);

        assertTrue(description.contains("요청 카메라 각도: 후면."));
        assertTrue(description.contains("요청 프레이밍: 전신."));
        assertTrue(description.contains("요청 포즈(피사체 신체 기준): 한쪽 다리를 앞으로 내딛고 상체를 자연스럽게 회전한 자세."));
        assertTrue(description.contains("포즈 설명의 방향은 피사체 몸통 기준이며 카메라 촬영 방향과 무관하다."));
        assertTrue(description.contains("요청 색온도: 쿨톤."));
        assertTrue(description.contains("요청 배경: 밝은 화이트 스튜디오와 부드러운 확산광으로 구성된 공간."));
        assertTrue(description.contains("요청 비율: 1:1."));
        assertFalse(description.contains("추천 포즈 1"));
        assertFalse(description.contains("추천 배경 1"));
        assertFalse(description.contains(".."));
    }

    @Test
    void modelCutAssetRoleCombinesFramingAndCameraAngle() throws Throwable {
        JsonNode references = objectMapper.createObjectNode();

        assertEquals(
                "FULL_BODY_FRONT",
                invokeModelMetadataAssetRole(objectMapper.readTree("""
                        {"framing":"FULL_BODY","cameraAngle":"EYE_LEVEL_FRONT"}
                        """), references)
        );
        assertEquals(
                "THREE_QUARTER_BODY_RIGHT_45",
                invokeModelMetadataAssetRole(objectMapper.readTree("""
                        {"framing":"THREE_QUARTER_BODY","cameraAngle":"RIGHT_THREE_QUARTER_45"}
                        """), references)
        );
        assertEquals(
                "HALF_BODY_BACK",
                invokeModelMetadataAssetRole(objectMapper.readTree("""
                        {"framing":"HALF_BODY","cameraAngle":"BACK_VIEW"}
                        """), references)
        );
    }

    @Test
    void multiShotIdsDetermineRequestedCount() throws Throwable {
        JsonNode references = objectMapper.readTree("""
                {"shotReferenceIds":[11,12,13,14]}
                """);

        List<Long> shotReferenceIds = invokeShotReferenceIds(references, "PRODUCT_CUT");
        int requestedCount = invokeRequestedCount(null, shotReferenceIds);

        assertEquals(List.of(11L, 12L, 13L, 14L), shotReferenceIds);
        assertEquals(4, requestedCount);
    }

    @Test
    void requestedCountMustMatchMultiShotSize() throws Throwable {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> invokeRequestedCount(4, List.of(11L, 12L))
        );

        assertEquals("requestedCount는 shotReferenceIds 개수와 같아야 합니다.", exception.getMessage());
    }

    @Test
    void singleAndMultiShotFieldsCannotBeUsedTogether() throws Exception {
        JsonNode references = objectMapper.readTree("""
                {"shotReferenceId":11,"shotReferenceIds":[11,12]}
                """);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> invokeShotReferenceIds(references, "PRODUCT_CUT")
        );

        assertEquals("shotReferenceId와 shotReferenceIds는 동시에 사용할 수 없습니다.", exception.getMessage());
    }

    @Test
    void multiShotIdsAreRejectedForModelCut() throws Exception {
        JsonNode references = objectMapper.readTree("""
                {"shotReferenceIds":[11,12]}
                """);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> invokeShotReferenceIds(references, "MODEL_CUT")
        );

        assertEquals("shotReferenceIds는 PRODUCT_CUT에서만 사용할 수 있습니다.", exception.getMessage());
    }

    @Test
    void multiShotCreatesOneIndependentProviderCallPerShot() throws Exception {
        ImageGenerationJobRepository jobRepository = mock(ImageGenerationJobRepository.class);
        ImageGenerationResultRepository resultRepository = mock(ImageGenerationResultRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        BrandMoodRepository brandMoodRepository = mock(BrandMoodRepository.class);
        AssetRepository assetRepository = mock(AssetRepository.class);
        ReferenceAssetRepository referenceAssetRepository = mock(ReferenceAssetRepository.class);
        SizeRepository sizeRepository = mock(SizeRepository.class);
        SizeMeasurementRepository sizeMeasurementRepository = mock(SizeMeasurementRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ImageGenerationWorker imageGenerationWorker = mock(ImageGenerationWorker.class);
        GcsService gcsService = mock(GcsService.class);
        GoogleAiImageProvider imageProvider = mock(GoogleAiImageProvider.class);

        ImageGenerationService multiShotService = new ImageGenerationService(
                jobRepository,
                resultRepository,
                productRepository,
                brandMoodRepository,
                assetRepository,
                referenceAssetRepository,
                sizeRepository,
                sizeMeasurementRepository,
                usersRepository,
                imageGenerationWorker,
                gcsService,
                imageProvider
        );
        Brand brand = mock(Brand.class);
        User user = mock(User.class);
        Product product = mock(Product.class);
        BrandMood brandMood = mock(BrandMood.class);
        when(brand.getId()).thenReturn(1L);
        when(user.getBrand()).thenReturn(brand);
        when(usersRepository.findByLoginId("tester")).thenReturn(Optional.of(user));
        when(productRepository.findByIdAndBrand_Id(1L, 1L)).thenReturn(Optional.of(product));
        when(brandMoodRepository.findTopByBrandIdAndActiveTrueOrderByVersionDesc(1L))
                .thenReturn(Optional.of(brandMood));
        when(brandMood.getId()).thenReturn(1L);
        when(brandMood.getBrandInstruction()).thenReturn("Keep the brand mood consistent.");
        when(imageProvider.getImageModel()).thenReturn("gemini-test-image");

        when(referenceAssetRepository.findById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            ReferenceAsset referenceAsset = mock(ReferenceAsset.class);
            when(referenceAsset.getId()).thenReturn(id);
            when(referenceAsset.getActive()).thenReturn(true);
            when(referenceAsset.getReferenceKey()).thenReturn("SHOT_" + id);
            when(referenceAsset.getObjectName()).thenReturn("reference/" + id + ".png");
            when(referenceAsset.getContentType()).thenReturn("image/png");
            return Optional.of(referenceAsset);
        });
        when(gcsService.download(anyString())).thenAnswer(invocation ->
                invocation.<String>getArgument(0).getBytes()
        );
        AtomicReference<ImageGenerationJob> savedJob = new AtomicReference<>();
        when(jobRepository.save(any(ImageGenerationJob.class))).thenAnswer(invocation -> {
            ImageGenerationJob job = invocation.getArgument(0);
            if (savedJob.get() == null) {
                setField(job, "id", 100L);
                savedJob.set(job);
            }
            return job;
        });
        ImageGenerationJobCreateReqDto request = new ImageGenerationJobCreateReqDto();
        request.setProductId(1L);
        request.setCutType("PRODUCT_CUT");
        request.setPrompt("Generate each selected product shot.");
        request.setUserOptionsJson(objectMapper.readTree("""
                {"aspectRatio":"RATIO_3_4"}
                """));
        request.setReferenceJson(objectMapper.readTree("""
                {"shotReferenceIds":[11,12,13,14]}
                """));

        ImageGenerationJobRespDto response = multiShotService.createJob(request, "tester");

        ArgumentCaptor<ImageGenerationWork> workCaptor = ArgumentCaptor.forClass(ImageGenerationWork.class);
        verify(imageGenerationWorker).submit(workCaptor.capture());

        ImageGenerationWork work = workCaptor.getValue();
        Set<String> prompts = work.tasks().stream()
                .map(ImageGenerationWork.Task::prompt)
                .collect(Collectors.toSet());
        Set<String> referencePayloads = work.tasks().stream()
                .map(ImageGenerationWork.Task::referenceImages)
                .peek(references -> assertEquals(1, references.size()))
                .map(references -> references.get(0))
                .peek(reference -> assertEquals("shotReferenceImage", reference.key()))
                .peek(reference -> assertEquals("SHOT_COMPOSITION_REFERENCE", reference.role()))
                .map(reference -> new String(reference.bytes()))
                .collect(Collectors.toSet());

        assertEquals(4, prompts.size());
        assertTrue(work.tasks().stream().allMatch(task -> "3:4".equals(task.aspectRatio())));
        assertTrue(prompts.stream().anyMatch(prompt -> prompt.contains("referenceAssetId 11 (SHOT_11)")));
        assertTrue(prompts.stream().anyMatch(prompt -> prompt.contains("referenceAssetId 14 (SHOT_14)")));
        assertEquals(Set.of(
                "reference/11.png",
                "reference/12.png",
                "reference/13.png",
                "reference/14.png"
        ), referencePayloads);
        assertEquals(4, response.getRequestedCount());
        assertEquals(0, response.getOutputCount());
        assertEquals("PENDING", response.getStatus());
        assertEquals(5, response.getProgressPercent());
    }

    @Test
    void measurementGuideUsesDescriptionPromptWithoutShotReferenceImage() throws Exception {
        ImageGenerationJobRepository jobRepository = mock(ImageGenerationJobRepository.class);
        ImageGenerationResultRepository resultRepository = mock(ImageGenerationResultRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        BrandMoodRepository brandMoodRepository = mock(BrandMoodRepository.class);
        AssetRepository assetRepository = mock(AssetRepository.class);
        ReferenceAssetRepository referenceAssetRepository = mock(ReferenceAssetRepository.class);
        SizeRepository sizeRepository = mock(SizeRepository.class);
        SizeMeasurementRepository sizeMeasurementRepository = mock(SizeMeasurementRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ImageGenerationWorker imageGenerationWorker = mock(ImageGenerationWorker.class);
        GcsService gcsService = mock(GcsService.class);
        GoogleAiImageProvider imageProvider = mock(GoogleAiImageProvider.class);

        ImageGenerationService measurementGuideService = new ImageGenerationService(
                jobRepository,
                resultRepository,
                productRepository,
                brandMoodRepository,
                assetRepository,
                referenceAssetRepository,
                sizeRepository,
                sizeMeasurementRepository,
                usersRepository,
                imageGenerationWorker,
                gcsService,
                imageProvider
        );
        Brand brand = mock(Brand.class);
        User user = mock(User.class);
        Product product = mock(Product.class);
        BrandMood brandMood = mock(BrandMood.class);
        ReferenceAsset measurementGuide = mock(ReferenceAsset.class);
        when(brand.getId()).thenReturn(1L);
        when(user.getBrand()).thenReturn(brand);
        when(usersRepository.findByLoginId("tester")).thenReturn(Optional.of(user));
        when(productRepository.findByIdAndBrand_Id(1L, 1L)).thenReturn(Optional.of(product));
        when(brandMoodRepository.findTopByBrandIdAndActiveTrueOrderByVersionDesc(1L))
                .thenReturn(Optional.of(brandMood));
        when(brandMood.getId()).thenReturn(1L);
        when(brandMood.getBrandInstruction()).thenReturn("Keep the brand mood consistent.");
        when(imageProvider.getImageModel()).thenReturn("gemini-test-image");
        when(measurementGuide.getId()).thenReturn(86L);
        when(measurementGuide.getActive()).thenReturn(true);
        when(measurementGuide.getReferenceKey()).thenReturn("REF_PRODUCT_TEMPLATE_MEASUREMENT_GUIDE");
        when(measurementGuide.getDescription()).thenReturn("상품 중앙 정렬과 외곽 점선 측정선을 사용하는 실측 안내 구도.");
        when(referenceAssetRepository.findById(86L)).thenReturn(Optional.of(measurementGuide));
        Size freeSize = mock(Size.class);
        SizeMeasurement shoulder = mock(SizeMeasurement.class);
        SizeMeasurement length = mock(SizeMeasurement.class);
        when(freeSize.getId()).thenReturn(20L);
        when(shoulder.getName()).thenReturn("어깨너비");
        when(length.getName()).thenReturn("총장");
        when(sizeRepository.findByProductIdOrderByIdAsc(1L)).thenReturn(List.of(freeSize));
        when(sizeMeasurementRepository.findBySizeIdInOrderByIdAsc(List.of(20L)))
                .thenReturn(List.of(shoulder, length));
        when(jobRepository.save(any(ImageGenerationJob.class))).thenAnswer(invocation -> {
            ImageGenerationJob job = invocation.getArgument(0);
            if (job.getId() == null) {
                setField(job, "id", 101L);
            }
            return job;
        });
        ImageGenerationJobCreateReqDto request = new ImageGenerationJobCreateReqDto();
        request.setProductId(1L);
        request.setCutType("PRODUCT_CUT");
        request.setPrompt("_");
        request.setUserOptionsJson(objectMapper.readTree("""
                {"aspectRatio":"RATIO_3_4"}
                """));
        request.setReferenceJson(objectMapper.readTree("""
                {"shotReferenceIds":[86]}
                """));

        measurementGuideService.createJob(request, "tester");

        ArgumentCaptor<ImageGenerationWork> workCaptor = ArgumentCaptor.forClass(ImageGenerationWork.class);
        verify(imageGenerationWorker).submit(workCaptor.capture());

        ImageGenerationWork work = workCaptor.getValue();
        assertEquals(1, work.tasks().size());
        assertTrue(work.tasks().get(0).referenceImages().isEmpty());
        assertTrue(work.tasks().get(0).prompt().contains("MEASUREMENT GUIDE PRODUCT IMAGE"));
        assertTrue(work.tasks().get(0).prompt().contains("상품 중앙 정렬과 외곽 점선 측정선을 사용하는 실측 안내 구도."));
        assertTrue(work.tasks().get(0).prompt().contains("[INTERNAL MEASUREMENT LINE CONTROL DATA - DO NOT RENDER]"));
        assertTrue(work.tasks().get(0).prompt().contains("- horizontal shoulder-width guide line across the shoulder endpoints"));
        assertTrue(work.tasks().get(0).prompt().contains("- vertical body-length guide line from the top shoulder or neckline area to the bottom hem"));
        assertTrue(work.tasks().get(0).prompt().contains("Do not render Korean text, English text, measurement names, labels, numbers, size values, captions, callouts, or arrows."));
        assertFalse(work.tasks().get(0).prompt().contains("- 어깨너비"));
        assertFalse(work.tasks().get(0).prompt().contains("- 총장"));
        assertFalse(work.tasks().get(0).prompt().contains("[SYSTEM INSTRUCTION]"));
        assertFalse(work.tasks().get(0).prompt().contains("[BRAND INSTRUCTION]"));
        assertFalse(work.tasks().get(0).prompt().contains("shotReferenceImage = SHOT_COMPOSITION_REFERENCE"));
    }

    private void invokeMetadataBuilder(String methodName, JsonNode emptyJson) throws Throwable {
        Method method = ImageGenerationService.class.getDeclaredMethod(methodName, JsonNode.class, JsonNode.class);
        method.setAccessible(true);

        try {
            method.invoke(service, emptyJson, emptyJson);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private String invokeSystemInstruction(String cutType) throws Exception {
        Method method = ImageGenerationService.class.getDeclaredMethod("buildSystemInstruction", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, cutType);
    }

    private String invokeUserOptionsInstruction(String cutType, JsonNode userOptions) throws Exception {
        ObjectNode normalizedOptions = userOptions == null
                ? objectMapper.createObjectNode()
                : (ObjectNode) userOptions.deepCopy();
        if (!normalizedOptions.hasNonNull("aspectRatio")) {
            normalizedOptions.put("aspectRatio", "RATIO_1_1");
        }

        Method method = ImageGenerationService.class.getDeclaredMethod(
                "buildUserOptionsInstruction",
                String.class,
                JsonNode.class
        );
        method.setAccessible(true);
        return (String) method.invoke(service, cutType, normalizedOptions);
    }

    private void assertCameraInstructionContains(String cameraAngle, String expected) throws Exception {
        JsonNode userOptions = objectMapper.readTree("""
                {"cameraAngle":"%s"}
                """.formatted(cameraAngle));

        String instruction = invokeUserOptionsInstruction("MODEL_CUT", userOptions);

        assertTrue(instruction.contains(expected));
    }

    private void assertFramingInstructionContains(String framing, String expected) throws Exception {
        JsonNode userOptions = objectMapper.readTree("""
                {"framing":"%s"}
                """.formatted(framing));

        String instruction = invokeUserOptionsInstruction("MODEL_CUT", userOptions);

        assertTrue(instruction.contains(expected));
        assertTrue(instruction.contains("피사체의 중심을 화면 가로·세로 중앙에 고정한다"));
    }

    private String invokeAspectRatio(JsonNode userOptions) throws Throwable {
        Method method = ImageGenerationService.class.getDeclaredMethod("resolveAspectRatio", JsonNode.class);
        method.setAccessible(true);

        try {
            return (String) method.invoke(service, userOptions);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private String invokeReferenceInstructionWithPose() throws Exception {
        Class<?> referenceInputClass = Class.forName(
                "kr.co.daeun.avenstudiobe.domain.imagegeneration.service."
                        + "ImageGenerationService$ReferenceAssetInput"
        );
        var constructor = referenceInputClass.getDeclaredConstructor(
                String.class,
                String.class,
                String.class,
                byte[].class,
                String.class
        );
        constructor.setAccessible(true);
        Object poseReference = constructor.newInstance(
                "poseImage",
                "pose reference",
                "POSE_REFERENCE",
                new byte[]{1},
                "image/png"
        );
        Method method = ImageGenerationService.class.getDeclaredMethod(
                "buildReferenceInstruction",
                String.class,
                List.class
        );
        method.setAccessible(true);
        return (String) method.invoke(service, "MODEL_CUT", List.of(poseReference));
    }

    private String invokeFinalOutputInstruction(
            String cutType,
            String cameraAngle,
            String framing,
            String aspectRatio
    ) throws Exception {
        Method method = ImageGenerationService.class.getDeclaredMethod(
                "buildFinalOutputInstruction",
                String.class,
                String.class,
                String.class,
                String.class
        );
        method.setAccessible(true);
        return (String) method.invoke(service, cutType, cameraAngle, framing, aspectRatio);
    }

    private String invokeModelMetadataDescription(
            ImageGenerationService targetService,
            JsonNode userOptions,
            JsonNode references
    ) throws Throwable {
        Method metadataMethod = ImageGenerationService.class.getDeclaredMethod(
                "buildModelCutMetadata",
                JsonNode.class,
                JsonNode.class
        );
        metadataMethod.setAccessible(true);

        Object metadata;
        try {
            metadata = metadataMethod.invoke(targetService, userOptions, references);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }

        Method descriptionMethod = metadata.getClass().getDeclaredMethod("description");
        descriptionMethod.setAccessible(true);
        return (String) descriptionMethod.invoke(metadata);
    }

    private String invokeModelMetadataAssetRole(JsonNode userOptions, JsonNode references) throws Throwable {
        Method metadataMethod = ImageGenerationService.class.getDeclaredMethod(
                "buildModelCutMetadata",
                JsonNode.class,
                JsonNode.class
        );
        metadataMethod.setAccessible(true);

        Object metadata;
        try {
            metadata = metadataMethod.invoke(service, userOptions, references);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }

        Method assetRoleMethod = metadata.getClass().getDeclaredMethod("assetRole");
        assetRoleMethod.setAccessible(true);
        return (String) assetRoleMethod.invoke(metadata);
    }

    @SuppressWarnings("unchecked")
    private List<Long> invokeShotReferenceIds(JsonNode references, String cutType) throws Throwable {
        Method method = ImageGenerationService.class.getDeclaredMethod(
                "extractShotReferenceIds",
                JsonNode.class,
                String.class
        );
        method.setAccessible(true);

        try {
            return (List<Long>) method.invoke(service, references, cutType);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private int invokeRequestedCount(Integer requestedCount, List<Long> shotReferenceIds) throws Throwable {
        Method method = ImageGenerationService.class.getDeclaredMethod(
                "resolveRequestedCount",
                Integer.class,
                List.class
        );
        method.setAccessible(true);

        try {
            return (int) method.invoke(service, requestedCount, shotReferenceIds);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
