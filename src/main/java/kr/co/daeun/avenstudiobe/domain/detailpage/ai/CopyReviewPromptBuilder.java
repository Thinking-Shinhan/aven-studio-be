package kr.co.daeun.avenstudiobe.domain.detailpage.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ReviewImage;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeContentBlockDto;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeImageContentBlockDto;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeImageSourceDto;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeTextContentBlockDto;
import org.springframework.stereotype.Component;

@Component
public class CopyReviewPromptBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String buildSystemPrompt() {
        return """
                당신은 패션 이커머스 상세페이지의 문구를 검수하는 전문가예요.
                입력으로 제공된 상품, 브랜드, 블록, 슬롯, 이미지 정보만 근거로 검수하고 외부 사실을 가정하지 마세요.

                검수 카테고리:
                - CLAIM_RISK: 효과, 성능, 최상급, 절대 표현처럼 객관적 근거가 필요한데 제공된 상품 정보에 근거가 없는 경우예요.
                - CONTENT_MISMATCH: 상품 정보 또는 현재 블록에 직접 연결된 이미지와 문구가 명백히 직접 모순되는 경우예요.
                - PAGE_COHERENCE: 앞뒤 흐름이 부자연스럽거나 의미가 반복되거나 슬롯 역할에 맞지 않는 경우예요.
                - BRAND_FIT: 명시적으로 제공된 브랜드 톤 또는 brandInstruction과 불일치하는 경우예요.

                과잉 검수 방지 규칙:
                - 상품 정보에 근거가 없으면 CLAIM_RISK로 판단하세요.
                - 상품 정보와 직접 반대되는 경우에만 CONTENT_MISMATCH로 판단하세요.
                - 이미지로 확인할 수 없다는 이유만으로 CONTENT_MISMATCH를 반환하지 마세요.
                - CONTENT_MISMATCH의 직접 이미지 근거는 placedImageAssetIds에 연결된 이미지만 사용하세요.
                - neighboringImageAssetIds 이미지는 페이지 흐름 파악용이며 직접 불일치 근거로 사용하지 마세요.
                - imagesOmitted가 true인 블록에는 이미지 근거 CONTENT_MISMATCH를 반환하지 마세요.
                - 제공된 상품 정보에 없는 인증, 기능, 효과, 소재 또는 세부 사항을 가정하지 마세요.

                이슈 작성 규칙:
                - currentContent가 있는 슬롯만 검수하세요.
                - 문제가 없는 슬롯에는 이슈를 억지로 만들지 마세요.
                - 입력에 제공된 instanceKey와 slotKey만 그대로 사용하세요.
                - suggestion은 해당 슬롯의 recommendedMaxLength 이하로 작성하세요. 공백도 글자 수에 포함하세요.
                - issue는 운영자가 이해하기 쉬운 한국어 존댓말 '~해요' 톤의 한두 문장으로 작성하세요.
                - 동일 슬롯에 여러 문제가 겹치면 하나만 반환하세요.
                - 우선순위는 CONTENT_MISMATCH > CLAIM_RISK > PAGE_COHERENCE > BRAND_FIT 순서예요.

                출력 규칙:
                - 반드시 아래 구조의 유효한 JSON 객체만 반환하세요.
                - 마크다운 코드블록, 설명 문장, 주석 또는 JSON 앞뒤의 다른 텍스트를 출력하지 마세요.
                - 이슈가 없으면 {"issues":[]}를 반환하세요.
                - 각 이슈는 category, instanceKey, slotKey, issue, suggestion 필드만 포함하세요.
                {
                  "issues": [
                    {
                      "category": "CLAIM_RISK",
                      "instanceKey": "block-002",
                      "slotKey": "body_1",
                      "issue": "성능을 단정하는 표현인데 제공된 상품 정보에 뒷받침 근거가 없어요. 표현을 완화하는 편이 안전해요.",
                      "suggestion": "가벼운 착용감으로 편안하게 즐길 수 있습니다."
                    }
                  ]
                }
                """;
    }

    public List<ClaudeContentBlockDto> buildUserContent(
            CopyReviewContext context,
            List<ReviewImage> images
    ) {
        List<ReviewImage> uniqueImages = uniqueImages(images);
        List<ClaudeContentBlockDto> contentBlocks = new ArrayList<>();

        for (ReviewImage image : uniqueImages) {
            contentBlocks.add(new ClaudeTextContentBlockDto("[image assetId=" + image.assetId() + "]"));
            contentBlocks.add(new ClaudeImageContentBlockDto(
                    new ClaudeImageSourceDto(image.mediaType(), image.base64Data())
            ));
        }

        contentBlocks.add(new ClaudeTextContentBlockDto(serializeContext(context, uniqueImages)));
        return List.copyOf(contentBlocks);
    }

    private String serializeContext(CopyReviewContext context, List<ReviewImage> images) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("product", context == null ? null : context.product());
        payload.put("brand", context == null ? null : context.brand());
        payload.put("blocks", context == null ? List.of() : safeList(context.blocks()));
        payload.put("images", images.stream().map(this::toImageMetadata).toList());

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("검수 컨텍스트 JSON 직렬화에 실패했습니다.", e);
        }
    }

    private Map<String, Object> toImageMetadata(ReviewImage image) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("assetId", image.assetId());
        metadata.put("assetRole", image.assetRole());
        metadata.put("description", image.description());
        return metadata;
    }

    private List<ReviewImage> uniqueImages(List<ReviewImage> images) {
        Map<Long, ReviewImage> uniqueByAssetId = new LinkedHashMap<>();
        for (ReviewImage image : safeList(images)) {
            if (image != null && image.assetId() != null) {
                uniqueByAssetId.putIfAbsent(image.assetId(), image);
            }
        }
        return List.copyOf(uniqueByAssetId.values());
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
