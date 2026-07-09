package kr.co.daeun.avenstudiobe.domain.imagegeneration.service;

import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ImageGenerationOptionsRespDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.OptionRespDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ReferenceAssetListRespDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ReferenceAssetRespDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ShotTemplateListRespDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ShotTemplateRespDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.entity.ReferenceAsset;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.repository.ReferenceAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ImageGenerationOptionService {

    private final ReferenceAssetRepository referenceAssetRepository;

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

    public ShotTemplateListRespDto getShotTemplates(String cutType) {
        String normalizedCutType = normalizeCutType(cutType);

        if ("MODEL_CUT".equals(normalizedCutType)) {
            return new ShotTemplateListRespDto(normalizedCutType, modelCutShotTemplates());
        }

        return new ShotTemplateListRespDto(normalizedCutType, productCutShotTemplates());
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

    private List<ShotTemplateRespDto> productCutShotTemplates() {
        return List.of(
                shot("FRONT", "정면", "PRODUCT_CUT", "상품의 정면 전체가 명확하게 보이는 제품컷으로 생성한다.", "전면 실루엣 확인용"),
                shot("BACK", "후면", "PRODUCT_CUT", "상품의 후면 전체가 명확하게 보이는 제품컷으로 생성한다.", "후면 실루엣 확인용"),
                shot("SIDE", "측면", "PRODUCT_CUT", "상품의 측면 구조와 두께감이 보이는 제품컷으로 생성한다.", "측면 구조 확인용"),
                shot("THREE_QUARTER", "45도", "PRODUCT_CUT", "상품을 45도 사선 각도에서 입체감 있게 보여주는 제품컷으로 생성한다.", "입체 실루엣 확인용"),
                shot("FLAT_LAY", "플랫레이", "PRODUCT_CUT", "상품을 바닥에 평평하게 놓은 플랫레이 제품컷으로 생성한다.", "전체 형태 확인용"),
                shot("HANGING", "행잉", "PRODUCT_CUT", "상품을 옷걸이에 건 상태의 행잉 제품컷으로 생성한다.", "걸린 상태 확인용"),
                shot("TOP_VIEW", "탑뷰", "PRODUCT_CUT", "상품을 위에서 내려다본 탑뷰 제품컷으로 생성한다.", "상단 구조 확인용"),
                shot("UNDER_BRIM", "챙 안쪽", "PRODUCT_CUT", "모자 챙 안쪽과 하단 구조가 보이는 제품 디테일컷으로 생성한다.", "모자 디테일 확인용"),
                shot("DETAIL", "디테일", "PRODUCT_CUT", "상품의 핵심 디테일 영역을 확대해서 보여주는 제품 디테일컷으로 생성한다.", "디테일 확대용"),
                shot("LOGO", "로고", "PRODUCT_CUT", "상품의 로고 위치와 자수 또는 프린트 질감이 보이도록 확대한다.", "로고 확인용"),
                shot("FABRIC", "소재", "PRODUCT_CUT", "상품 원단의 조직감, 두께감, 표면 질감이 보이도록 확대한다.", "소재 확인용"),
                shot("CONSTRUCTION", "봉제", "PRODUCT_CUT", "봉제선, 시접, 마감 구조가 보이도록 확대한다.", "구조 확인용")
        );
    }

    private List<ShotTemplateRespDto> modelCutShotTemplates() {
        return List.of(
                shot("FRONT_STANDING", "정면", "MODEL_CUT", "모델이 정면을 바라보는 자연스러운 착용컷으로 생성한다.", "기본 착용 확인용"),
                shot("SIDE_STANDING", "측면", "MODEL_CUT", "모델의 측면 실루엣과 상품 핏이 보이는 착용컷으로 생성한다.", "측면 핏 확인용"),
                shot("BACK_STANDING", "후면", "MODEL_CUT", "모델의 후면 실루엣과 상품 뒷면이 보이는 착용컷으로 생성한다.", "후면 핏 확인용"),
                shot("WALKING", "워킹", "MODEL_CUT", "모델이 자연스럽게 걷는 활동감 있는 착용컷으로 생성한다.", "활동감 확인용"),
                shot("SITTING", "앉기", "MODEL_CUT", "모델이 앉아 있는 자세의 착용컷으로 생성한다.", "착석 핏 확인용")
        );
    }

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

    private String normalizeAssetType(String assetType) {
        if (assetType == null || assetType.isBlank()) {
            return null;
        }

        String normalizedAssetType = assetType.trim().toUpperCase();

        if (!List.of("MODEL", "BACKGROUND", "POSE", "SHOT_REFERENCE").contains(normalizedAssetType)) {
            throw new IllegalArgumentException("지원하지 않는 레퍼런스 에셋 유형입니다.");
        }

        return normalizedAssetType;
    }

    private OptionRespDto option(String key, String label, String description) {
        return new OptionRespDto(key, label, description);
    }

    private ShotTemplateRespDto shot(String key, String label, String cutType, String promptText, String description) {
        return new ShotTemplateRespDto(key, label, cutType, promptText, description);
    }

    private ReferenceAssetRespDto toReferenceAssetResponse(ReferenceAsset referenceAsset) {
        return new ReferenceAssetRespDto(
                referenceAsset.getId(),
                referenceAsset.getReferenceKey(),
                referenceAsset.getReferenceType(),
                referenceAsset.getName(),
                referenceAsset.getDescription(),
                referenceAsset.getImageUrl()
        );
    }
}
