package kr.co.daeun.avenstudiobe.domain.detailpage.ai;
import java.util.LinkedHashMap;
import java.util.Map;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AssetPlacementCandidate;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoPlacementContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class AutoPlacementPromptBuilder {

    private final JsonMapper jsonMapper;

    public String buildSystemPrompt() {
        return """
                You are an expert fashion ecommerce detail-page image curator.

                Goal:
                - Place the given product images into the given detail-page image slots.
                - Decide from the overall page order, template context, product information, assetRole, and description.
                - The first available image slot should usually receive the strongest representative image.
                - For fashion products, prefer a clear model-worn hero image first when available.
                - If no suitable model-worn hero image exists, use a clear full-product front or overview image.
                - After the hero image, prefer additional model-worn mood, movement, styling, or use-case images.
                - Then place product overview images such as front, back, side, or flat-lay product shots.
                - Place detail, material, logo, stitching, construction, texture, care-label, and close-up images later.
                - Use each assetId at most once.
                - Do not fill every slot if the image is insufficient or unsuitable.

                Constraints:
                - Use only provided assetId values.
                - Use only provided instanceKey and slotKey values.
                - Do not include or invent image URL values.
                - Return JSON only.
                - Do not wrap the response in a markdown code block.

                Response format:
                {
                  "placements": [
                    {
                      "instanceKey": "block-001",
                      "slotKey": "image_1",
                      "assetId": 25,
                      "reason": "Short internal reason"
                    }
                  ]
                }
                """;
    }

    public String buildUserPrompt(AutoPlacementContext context) {
        try {
            return jsonMapper.writeValueAsString(toPromptPayload(context));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to build auto placement prompt.", e);
        }
    }

    private Map<String, Object> toPromptPayload(AutoPlacementContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("product", context.productInfo());
        payload.put("slots", context.slots());
        payload.put("assets", context.assets().stream()
                .map(this::toPromptAsset)
                .toList());
        return payload;
    }

    private Map<String, Object> toPromptAsset(AssetPlacementCandidate asset) {
        Map<String, Object> promptAsset = new LinkedHashMap<>();
        promptAsset.put("assetId", asset.assetId());
        promptAsset.put("assetRole", asset.assetRole());
        promptAsset.put("description", asset.description());
        return promptAsset;
    }
}
