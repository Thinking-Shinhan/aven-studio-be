package kr.co.daeun.avenstudiobe.domain.detailpage.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AssetPlacementCandidate;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoPlacementContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.PlacementSlotKey;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.PlacementSlot;
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
                - Slots with status FILLED are existing placements. Use them only as page context.
                - Slots with status CONTEXT_EMPTY are empty non-target slots. Use them only as page context.
                - Place new images only into slots with status EMPTY.
                - The first available image slot should usually receive the strongest representative image.
                - For fashion products, prefer a clear model-worn hero image first when available.
                - If no suitable model-worn hero image exists, use a clear full-product front or overview image.
                - After the hero image, prefer additional model-worn mood, movement, styling, or use-case images.
                - Then place product overview images such as front, back, side, or flat-lay product shots.
                - Place detail, material, logo, stitching, construction, texture, care-label, and close-up images later.
                - Use each assetId at most once.
                - Do not fill every slot if the image is insufficient or unsuitable.

                assetRole meanings for generated images:
                - Model-worn framing: FULL_BODY, THREE_QUARTER_BODY, HALF_BODY, CLOSE_UP, WIDE_MARGIN.
                - Product overview: FRONT_VIEW, THREE_QUARTER, BACK_VIEW, SIDE_VIEW, FLAT_LAY, HANGING, GHOST_MANNEQUIN, FOLDED, PRODUCT_CUT.
                - Product detail: DETAIL_CLOSEUP. Material detail: FABRIC_MACRO.
                - Do not make the entire page model-only when suitable unused product-overview or detail images are available.

                Constraints:
                - Use only provided assetId values.
                - Use only provided instanceKey and slotKey values.
                - Do not change FILLED slots.
                - Do not return CONTEXT_EMPTY slots in placements.
                - Do not return FILLED slots in placements.
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

                Copy style consistency:
                - Use currentContent in non-generation-target slots as style reference, prioritizing the same textRole and nearby blocks.
                - Preserve recurring patterns in tone, language, casing, sentence form, and length without copying phrases verbatim.
                - If references are insufficient or inconsistent, write naturally in the product's primary language according to the textRole.
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
        Map<Long, AssetPlacementCandidate> assetMap = context.assets().stream()
                .filter(asset -> asset.assetId() != null)
                .collect(Collectors.toMap(
                        AssetPlacementCandidate::assetId,
                        Function.identity(),
                        (first, ignored) -> first
                ));
        Set<Long> filledAssetIds = context.slots().stream()
                .map(PlacementSlot::assetId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<PlacementSlotKey> targetSlotKeys = context.targetSlots().stream()
                .map(slot -> new PlacementSlotKey(slot.instanceKey(), slot.slotKey()))
                .collect(Collectors.toSet());

        payload.put("product", context.productInfo());
        payload.put("slots", context.slots().stream()
                .map(slot -> toPromptSlot(slot, assetMap, targetSlotKeys))
                .toList());
        payload.put("availableAssets", context.assets().stream()
                .filter(asset -> asset.assetId() != null && !filledAssetIds.contains(asset.assetId()))
                .map(this::toPromptAsset)
                .toList());
        return payload;
    }

    private Map<String, Object> toPromptSlot(
            PlacementSlot slot,
            Map<Long, AssetPlacementCandidate> assetMap,
            Set<PlacementSlotKey> targetSlotKeys
    ) {
        Map<String, Object> promptSlot = new LinkedHashMap<>();
        promptSlot.put("globalSlotOrder", slot.globalSlotOrder());
        promptSlot.put("blockOrder", slot.blockOrder());
        promptSlot.put("instanceKey", slot.instanceKey());
        promptSlot.put("templateKey", slot.templateKey());
        promptSlot.put("templateType", slot.templateType());
        promptSlot.put("slotKey", slot.slotKey());
        promptSlot.put("slotOrder", slot.slotOrder());
        promptSlot.put("status", resolveSlotStatus(slot, targetSlotKeys));

        if (slot.isFilled()) {
            AssetPlacementCandidate asset = assetMap.get(slot.assetId());
            promptSlot.put("assetId", slot.assetId());
            promptSlot.put("assetRole", asset == null ? null : asset.assetRole());
            promptSlot.put("description", asset == null ? null : asset.description());
        }

        return promptSlot;
    }

    private String resolveSlotStatus(PlacementSlot slot, Set<PlacementSlotKey> targetSlotKeys) {
        if (slot.isFilled()) {
            return "FILLED";
        }

        return targetSlotKeys.contains(new PlacementSlotKey(slot.instanceKey(), slot.slotKey()))
                ? "EMPTY"
                : "CONTEXT_EMPTY";
    }

    private Map<String, Object> toPromptAsset(AssetPlacementCandidate asset) {
        Map<String, Object> promptAsset = new LinkedHashMap<>();
        promptAsset.put("assetId", asset.assetId());
        promptAsset.put("assetRole", asset.assetRole());
        promptAsset.put("description", asset.description());
        return promptAsset;
    }
}
