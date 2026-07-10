package kr.co.daeun.avenstudiobe.domain.imagegeneration.service;

import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ImageGenerationOptionsRespDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.OptionRespDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ReferenceAssetListRespDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ReferenceAssetRespDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ShotTemplateListRespDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ShotTemplateRespDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.entity.ReferenceAsset;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.repository.ReferenceAssetRepository;
import kr.co.daeun.avenstudiobe.global.storage.GcsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ImageGenerationOptionService {

    private final ReferenceAssetRepository referenceAssetRepository;
    private final GcsService gcsService;

    public ImageGenerationOptionsRespDto getOptions() {
        return new ImageGenerationOptionsRespDto(
                List.of(
                        option("MODEL_CUT", "모델컷", "상품을 모델에게 착용시킨 이미지"),
                        option("PRODUCT_CUT", "제품컷", "모델 없이 상품 자체를 보여주는 이미지")
                ),
                List.of(
                        option("WARM", "웜톤", "따뜻한 색온도"),
                        option("NEUTRAL", "뉴트럴", "중립적인 색온도"),
                        option("COOL", "쿨톤", "차분한 차가운 색온도")
                ),
                List.of(
                        option("EYE_LEVEL_FRONT", "정면", "눈높이 기준 정면 구도"),
                        option("LEFT_THREE_QUARTER_45", "좌측 사선", "좌측 45도 사선 구도"),
                        option("RIGHT_THREE_QUARTER_45", "우측 사선", "우측 45도 사선 구도"),
                        option("BACK_VIEW", "후면", "뒤쪽에서 바라보는 구도")
                ),
                List.of(
                        option("FULL_BODY", "전신", "모델 또는 상품 전체가 보이는 구도"),
                        option("THREE_QUARTER_BODY", "3/4 전신", "무릎 아래까지 보이는 구도"),
                        option("HALF_BODY", "반신", "상반신과 허리 중심 구도"),
                        option("CLOSE_UP", "클로즈업", "디테일을 크게 보여주는 구도"),
                        option("WIDE_MARGIN", "여백 강조", "상품 주변 여백이 충분한 구도")
                ),
                List.of(
                        option("RATIO_1_1", "1:1", "정사각형"),
                        option("RATIO_2_3", "2:3", "세로형"),
                        option("RATIO_3_4", "3:4", "세로형"),
                        option("RATIO_4_5", "4:5", "이커머스 세로형"),
                        option("RATIO_16_9", "16:9", "가로형"),
                        option("RATIO_9_16", "9:16", "숏폼 세로형")
                ),
                List.of(
                        option("1", "1장", "단일 이미지 생성"),
                        option("2", "2장", "두 개 이미지 생성"),
                        option("3", "3장", "세 개 이미지 생성"),
                        option("4", "4장", "MVP 최대 출력 수")
                )
        );
    }

    public ShotTemplateListRespDto getShotTemplates() {
        List<ReferenceAsset> templates = referenceAssetRepository
                .findByReferenceTypeAndActiveTrueOrderBySortOrderAscIdAsc("SHOT_TEMPLATE");

<<<<<<< HEAD
        List<ShotTemplateRespDto> dtos = templates.stream()
                .map(this::toShotTemplateResponse)
                .toList();

        return new ShotTemplateListRespDto(dtos);
=======
        List<ReferenceAsset> templates = referenceAssetRepository
                .findByReferenceTypeAndCutTypeAndActiveTrueOrderBySortOrderAscIdAsc(
                        "SHOT_TEMPLATE", normalizedCutType);

        List<ShotTemplateRespDto> dtos = templates.stream()
                .map(this::toShotTemplateResponse)
                .toList();

        return new ShotTemplateListRespDto(normalizedCutType, dtos);
>>>>>>> develop
    }

    public ReferenceAssetListRespDto getReferenceAssets(String assetType) {
        String normalizedAssetType = normalizeAssetType(assetType);

        List<ReferenceAsset> referenceAssets = normalizedAssetType == null
                ? referenceAssetRepository.findByActiveTrueOrderBySortOrderAscIdAsc()
                : referenceAssetRepository.findByReferenceTypeAndActiveTrueOrderBySortOrderAscIdAsc(normalizedAssetType);

        List<ReferenceAssetRespDto> assets = referenceAssets.stream()
                .map(this::toReferenceAssetResponse)
                .toList();

        return new ReferenceAssetListRespDto(normalizedAssetType, assets);
    }

    @Transactional
    public ReferenceAssetRespDto uploadReferenceAsset(
            MultipartFile file,
            String assetType,
            String key,
            String label,
            String description,
            Integer sortOrder,
<<<<<<< HEAD
=======
            String cutType,
>>>>>>> develop
            String promptText
    ) {
        String normalizedAssetType = normalizeRequiredAssetType(assetType);
        String normalizedKey = normalizeReferenceKey(key);
        String normalizedLabel = normalizeLabel(label);
        String normalizedCutType = normalizeCutTypeOrNull(cutType);

        if (referenceAssetRepository.existsByReferenceKey(normalizedKey)) {
            throw new IllegalArgumentException("이미 사용 중인 레퍼런스 에셋 key입니다.");
        }

        if ("SHOT_TEMPLATE".equals(normalizedAssetType) && normalizedCutType == null) {
            throw new IllegalArgumentException("SHOT_TEMPLATE 타입은 cutType을 반드시 지정해야 합니다.");
        }

        GcsService.UploadedFile uploaded = gcsService.uploadReferenceAsset(file, normalizedAssetType);

        try {
            ReferenceAsset referenceAsset = ReferenceAsset.builder()
                    .referenceType(normalizedAssetType)
                    .referenceKey(normalizedKey)
                    .name(normalizedLabel)
                    .imageUrl(uploaded.fileUrl())
                    .objectName(uploaded.objectName())
                    .contentType(uploaded.contentType())
                    .description(description)
                    .active(true)
                    .sortOrder(sortOrder == null ? 0 : sortOrder)
<<<<<<< HEAD
=======
                    .cutType(normalizedCutType)
>>>>>>> develop
                    .promptText(promptText)
                    .build();

            return toReferenceAssetResponse(referenceAssetRepository.save(referenceAsset));

        } catch (RuntimeException e) {
            gcsService.deleteQuietly(uploaded.objectName());
            throw e;
        }
    }


<<<<<<< HEAD
=======

    private String normalizeCutType(String cutType) {
        if (cutType == null || cutType.isBlank()) {
            return "PRODUCT_CUT";
        }

        String normalizedCutType = cutType.trim().toUpperCase();

        if (!List.of("MODEL_CUT", "PRODUCT_CUT").contains(normalizedCutType)) {
            throw new IllegalArgumentException("지원하지 않는 컷 유형입니다.");
        }

        return normalizedCutType;
    }
>>>>>>> develop

    private String normalizeAssetType(String assetType) {
        if (assetType == null || assetType.isBlank()) {
            return null;
        }

        return normalizeRequiredAssetType(assetType);
    }

    private String normalizeRequiredAssetType(String assetType) {
        if (assetType == null || assetType.isBlank()) {
            throw new IllegalArgumentException("레퍼런스 에셋 유형을 입력해주세요.");
        }

        String normalizedAssetType = assetType.trim().toUpperCase();

        if (!List.of("MODEL", "BACKGROUND", "POSE", "SHOT_REFERENCE", "SHOT_TEMPLATE").contains(normalizedAssetType)) {
            throw new IllegalArgumentException("지원하지 않는 레퍼런스 에셋 유형입니다.");
        }

        return normalizedAssetType;
    }

    private String normalizeReferenceKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("레퍼런스 에셋 key를 입력해주세요.");
        }

        return key.trim();
    }

    private String normalizeLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("레퍼런스 에셋 이름을 입력해주세요.");
        }

        return label.trim();
    }

    private String normalizeCutTypeOrNull(String cutType) {
        if (cutType == null || cutType.isBlank()) {
            return null;
        }

        String normalized = cutType.trim().toUpperCase();

        if (!List.of("MODEL_CUT", "PRODUCT_CUT").contains(normalized)) {
            throw new IllegalArgumentException("지원하지 않는 컷 유형입니다.");
        }

        return normalized;
    }

    private OptionRespDto option(String key, String label, String description) {
        return new OptionRespDto(key, label, description);
    }

    private ShotTemplateRespDto toShotTemplateResponse(ReferenceAsset asset) {
        return new ShotTemplateRespDto(
                asset.getId(),
                asset.getReferenceKey(),
                asset.getName(),
<<<<<<< HEAD
=======
                asset.getCutType(),
>>>>>>> develop
                asset.getPromptText(),
                asset.getDescription(),
                getReferenceAssetImageUrl(asset)
        );
    }

    private ReferenceAssetRespDto toReferenceAssetResponse(ReferenceAsset referenceAsset) {
        return new ReferenceAssetRespDto(
                referenceAsset.getId(),
                referenceAsset.getReferenceKey(),
                referenceAsset.getReferenceType(),
                referenceAsset.getName(),
                referenceAsset.getDescription(),
                getReferenceAssetImageUrl(referenceAsset)
        );
    }

    private String getReferenceAssetImageUrl(ReferenceAsset referenceAsset) {
        if (referenceAsset.getObjectName() == null || referenceAsset.getObjectName().isBlank()) {
            return referenceAsset.getImageUrl();
        }

        return gcsService.generateSignedUrl(referenceAsset.getObjectName());
    }
}
