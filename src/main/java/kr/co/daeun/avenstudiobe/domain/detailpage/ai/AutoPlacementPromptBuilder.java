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
            - Slots with status CONTEXT_EMPTY are empty non-target slots. Use them as future page positions when planning the sequence, but do not place images into them.
            - Place new images only into slots with status EMPTY.
            - The earliest image slot in the full page by globalSlotOrder should usually receive the strongest representative image.
            - Do not treat the first EMPTY target slot as the first image slot of the page.
            - For fashion products, prefer a clear model-worn hero image first when available.
            - If no suitable model-worn hero image exists, use a clear full-product front or overview image.
            - Use each assetId at most once.
            - Do not fill every slot if the available images are insufficient or unsuitable.

            assetRole meanings for generated images:
            - Model-worn images: assetRole starts with a body-shot prefix (FULL_BODY, THREE_QUARTER_BODY, HALF_BODY, CLOSE_UP, WIDE_MARGIN, MODEL, MODEL_CUT), optionally followed by an angle/composition suffix (e.g. _FRONT, _BACK, _SIDE).
            - Product overview: PRODUCT_FRONT, PRODUCT_BACK, PRODUCT_IMAGE, FRONT_VIEW, THREE_QUARTER, BACK_VIEW, SIDE_VIEW, FLAT_LAY, HANGING, GHOST_MANNEQUIN, FOLDED, PRODUCT_CUT.
            - Product detail: DETAIL_CLOSEUP, DETAIL.
            - Material detail: FABRIC_MACRO.
            - Size info: SIZE_INFO.

            Decision process:
            - Follow these steps in order before selecting any asset.
                
            1. Analyze current page coverage.
               - Classify every FILLED slot as MODEL_WORN, PRODUCT_OVERVIEW, PRODUCT_DETAIL, or MATERIAL_DETAIL.
               - Determine which information is already represented on the page.
                
            2. Analyze remaining placement opportunities.
               - Count the current EMPTY slots and later CONTEXT_EMPTY slots.
               - Determine whether missing information can be placed in a later slot or whether the current EMPTY slot is the final opportunity.
                
            3. Assign an image category to each EMPTY slot.
               - Model-worn images should remain the majority category. But, A completed page should include at least one clear product-overview image when a suitable asset is available.
               - Product-detail and material-detail images are optional and should be considered after model-worn and product-overview coverage already exist.
               - If the current EMPTY slot is the final opportunity and the page has no product-overview image, assign product front image to that slot.
               - If suitable later slots still remain, missing product-overview or detail coverage may be deferred to a later slot.
            
            4. Select assets only after assigning a required category to each EMPTY slot.
               - Filter candidates to the assigned category, then group them by exact assetRole.
               - Treat assetRoles in FILLED slots and earlier planned placements as already represented.
               - Prefer one suitable asset from each unrepresented assetRole before selecting additional assets from a represented assetRole.
               - Within the same exact assetRole, prefer assets with "liked": true, then compare description, framing, pose, and styling.
               - Liked preference must never override exact assetRole diversity.

            Priority order:
            - Required page coverage and assigned category
            - Relative slot position and remaining opportunities
            - Coverage of distinct suitable exact assetRoles within the assigned category
            - Liked preference within the same exact assetRole
            - Description, framing, pose, and styling suitability
            - Additional assets from an already represented assetRole

            Do not skip directly to asset selection.

            Constraints:
            - If a slot's templateKey contains "size-info", you must select an asset whose assetRole is SIZE_INFO for that slot, and never place a SIZE_INFO asset into any other slot.
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
        if (Boolean.TRUE.equals(asset.liked())) {
            promptAsset.put("liked", true);
        }
        return promptAsset;
    }
}
