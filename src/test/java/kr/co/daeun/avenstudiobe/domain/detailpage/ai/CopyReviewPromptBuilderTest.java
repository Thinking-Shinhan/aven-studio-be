package kr.co.daeun.avenstudiobe.domain.detailpage.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.BrandReviewContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewContext.ProductReviewContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewContext.ReviewMaterialContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto.ReviewTextSlotReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ReviewAssetSource;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ReviewBlockContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ReviewImage;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeContentBlockDto;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeImageContentBlockDto;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeTextContentBlockDto;
import org.junit.jupiter.api.Test;

class CopyReviewPromptBuilderTest {

    private final CopyReviewPromptBuilder builder = new CopyReviewPromptBuilder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildsImagePairsBeforeTheExactReviewContextJson() throws Exception {
        ReviewImage first = new ReviewImage(25L, "MODEL_CUT", "model", "image/jpeg", "base64-a");
        ReviewImage duplicate = new ReviewImage(25L, "OTHER", "duplicate", "image/jpeg", "ignored");
        ReviewImage second = new ReviewImage(30L, "DETAIL", "detail", "image/jpeg", "base64-b");

        List<ClaudeContentBlockDto> content = builder.buildUserContent(
                context(),
                List.of(first, duplicate, second)
        );

        assertEquals(5, content.size());
        assertEquals("[image assetId=25]", ((ClaudeTextContentBlockDto) content.get(0)).text());
        assertEquals("base64-a", ((ClaudeImageContentBlockDto) content.get(1)).source().data());
        assertEquals("image/jpeg", ((ClaudeImageContentBlockDto) content.get(1)).source().mediaType());
        assertEquals("[image assetId=30]", ((ClaudeTextContentBlockDto) content.get(2)).text());
        assertEquals("base64-b", ((ClaudeImageContentBlockDto) content.get(3)).source().data());

        JsonNode payload = objectMapper.readTree(((ClaudeTextContentBlockDto) content.get(4)).text());
        assertEquals(Set.of("product", "brand", "blocks", "images"), fieldNames(payload));
        assertEquals("Jacket", payload.path("product").path("name").asText());
        assertEquals("폴리에스터", payload.path("product").path("materials").get(0).path("name").asText());
        assertEquals("Aven", payload.path("brand").path("brandName").asText());
        assertEquals(25L, payload.path("blocks").get(0).path("associatedImageAssetIds").get(0).asLong());
        assertEquals(30L, payload.path("blocks").get(0).path("neighboringImageAssetIds").get(0).asLong());

        JsonNode imageMetadata = payload.path("images").get(0);
        assertEquals(Set.of("assetId", "assetRole", "description"), fieldNames(imageMetadata));
        assertFalse(imageMetadata.has("role"));
        assertFalse(imageMetadata.has("instanceKey"));
        assertFalse(imageMetadata.has("slotKey"));
        assertFalse(imageMetadata.has("mediaType"));
        assertFalse(imageMetadata.has("base64Data"));
        assertFalse(imageMetadata.has("objectName"));
        assertFalse(payload.has("assetSources"));
    }

    @Test
    void systemPromptContainsEveryRequiredReviewBoundary() {
        String prompt = builder.buildSystemPrompt();

        assertTrue(prompt.contains("근거가 없으면 CLAIM_RISK"));
        assertTrue(prompt.contains("직접 반대되는 경우에만 CONTENT_MISMATCH"));
        assertTrue(prompt.contains("이미지로 확인할 수 없다는 이유만으로 CONTENT_MISMATCH"));
        assertTrue(prompt.contains("placedImageAssetIds"));
        assertTrue(prompt.contains("associatedImageAssetIds"));
        assertTrue(prompt.contains("현재 문구와 직접 연결된 근거"));
        assertTrue(prompt.contains("neighboringImageAssetIds"));
        assertTrue(prompt.contains("페이지 흐름 파악용"));
        assertTrue(prompt.contains("imagesOmitted가 true"));
        assertTrue(prompt.contains("인증, 기능, 효과, 소재"));
        assertTrue(prompt.contains("recommendedMaxLength 이하"));
        assertTrue(prompt.contains("'~해요' 톤의 한두 문장"));
        assertTrue(prompt.contains("CONTENT_MISMATCH > CLAIM_RISK > PAGE_COHERENCE > BRAND_FIT"));
        assertTrue(prompt.contains("마크다운 코드블록"));
        assertTrue(prompt.contains("{\"issues\":[]}"));
    }

    private CopyReviewContext context() {
        ProductReviewContext product = new ProductReviewContext(
                14L,
                "Jacket",
                "상의",
                "U",
                "Black",
                "description",
                "fit",
                "fabrication",
                List.of(new ReviewMaterialContext("폴리에스터", new BigDecimal("88"))),
                "있음",
                "매우좋음",
                "없음",
                "부드러움",
                "두꺼움"
        );
        BrandReviewContext brand = new BrandReviewContext(
                "Aven",
                "summary",
                "philosophy",
                List.of("미니멀"),
                "instruction",
                "note"
        );
        ReviewBlockContext block = new ReviewBlockContext(
                1,
                "block-1",
                "template-1",
                "MIXED",
                List.of(new ReviewTextSlotReqDto("headline", 1, "HEADLINE", 20, "current")),
                List.of(),
                List.of(25L),
                List.of(30L),
                false
        );
        return new CopyReviewContext(
                product,
                brand,
                List.of(block),
                List.of(new ReviewAssetSource(25L, "secret/object.webp", "image/webp", "MODEL_CUT", "model"))
        );
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
