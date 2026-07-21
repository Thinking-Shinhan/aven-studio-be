package kr.co.daeun.avenstudiobe.domain.detailpage.ai;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AssetCopyContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.BlockCopyContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyGenerationContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopySlotKey;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.TextSlotCopyContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.validator.CopyValidationError;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class CopyPromptBuilder {

    private final JsonMapper jsonMapper;

    public String buildSystemPrompt() {
        return """
                You are an expert fashion ecommerce detail-page copywriter for AVEN.

                Analysis order:
                - Read the product and brand context first.
                - Review all blocks in blockOrder order.
                - Use existing currentContent, referenceAssets, and the surrounding block flow to infer each slot's role.
                - Decide what each generationTarget=true slot should communicate before writing final copy.
                - Do not output your analysis process.

                Copywriting rules:
                - Generate copy only for slots where generationTarget=true.
                - Do not edit slots that have currentContent. Use them only to understand context.
                - If referenceAssets exists, prioritize the image descriptions when writing the copy.
                - Do not repeat the same product feature or benefit already used in earlier copy.
                - Do not infer functions, effects, materials, or details that are not present in the product information.
                - Make full use of recommendedMaxLength: aim for 80-100% of it. Do not stop at a short, single safe line.
                - Use instanceKey and slotKey exactly as provided in the input.

                Output format:
                - Return only a valid JSON array.
                - Do not include explanations, comments, markdown, or code fences.
                - Each array item must have exactly this shape:
                  { "instanceKey": "block-001", "slotKey": "headline", "content": "Generated copy" }
                - Every content value must be a single-line string.
                - Do not include newline or carriage-return characters (\\n or \\r) inside content.
                """;
    }

    public String buildUserMessage(CopyGenerationContext context) {
        try {
            return jsonMapper.writeValueAsString(toPromptPayload(context));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to build copy generation prompt.", e);
        }
    }

    public String buildCorrectionMessage(
            CopyGenerationContext context,
            String rawResponse,
            List<CopyValidationError> errors
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instruction", """
                The previous response failed validation.
                Return the full JSON array again for every generation target slot, not only the failed slots.
                Do not change instanceKey or slotKey.
                Keep copy that did not have errors as much as possible.
                The content must be non-blank and within recommendedMaxLength.
                Return pure JSON array only. Do not include explanations, comments, markdown, or code fences.
                """);
        payload.put("validationErrors", safeList(errors).stream()
                .map(this::toPromptError)
                .toList());
        payload.put("allTargetSlots", toTargetSlots(context));
        payload.put("previousRawResponse", rawResponse);
        payload.put("context", toPromptPayload(context));

        try {
            return jsonMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to build copy correction prompt.", e);
        }
    }

    private Map<String, Object> toPromptPayload(CopyGenerationContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("product", context == null ? null : context.productContext());
        payload.put("blocks", context == null ? List.of() : context.sortedBlocks().stream()
                .map(this::toPromptBlock)
                .toList());
        return payload;
    }

    private Map<String, Object> toPromptBlock(BlockCopyContext block) {
        Map<String, Object> promptBlock = new LinkedHashMap<>();
        promptBlock.put("blockOrder", block.blockOrder());
        promptBlock.put("instanceKey", block.instanceKey());
        promptBlock.put("templateKey", block.templateKey());
        promptBlock.put("placedAssets", block.placedAssets().stream()
                .map(this::toPromptAsset)
                .toList());
        promptBlock.put("referenceAssets", block.referenceAssets().stream()
                .map(this::toPromptAsset)
                .toList());
        promptBlock.put("textSlots", block.textSlots().stream()
                .map(this::toPromptTextSlot)
                .toList());
        return promptBlock;
    }

    private Map<String, Object> toPromptAsset(AssetCopyContext asset) {
        Map<String, Object> promptAsset = new LinkedHashMap<>();
        promptAsset.put("assetId", asset.assetId());
        promptAsset.put("assetRole", asset.assetRole());
        promptAsset.put("description", asset.description());
        return promptAsset;
    }

    private Map<String, Object> toPromptTextSlot(TextSlotCopyContext slot) {
        Map<String, Object> promptSlot = new LinkedHashMap<>();
        promptSlot.put("slotKey", slot.slotKey());
        promptSlot.put("slotOrder", slot.slotOrder());
        promptSlot.put("textRole", slot.textRole());
        promptSlot.put("recommendedMaxLength", slot.recommendedMaxLength());
        promptSlot.put("currentContent", slot.currentContent());
        promptSlot.put("generationTarget", slot.generationTarget());
        return promptSlot;
    }

    private Map<String, Object> toPromptError(CopyValidationError error) {
        Map<String, Object> promptError = new LinkedHashMap<>();
        promptError.put("code", error.code());
        promptError.put("instanceKey", error.instanceKey());
        promptError.put("slotKey", error.slotKey());
        promptError.put("message", error.message());
        return promptError;
    }

    private List<Map<String, Object>> toTargetSlots(CopyGenerationContext context) {
        if (context == null || context.targetSlots().isEmpty()) {
            return List.of();
        }

        Set<CopySlotKey> targetSlots = context.targetSlots();

        return context.sortedBlocks().stream()
                .flatMap(block -> block.textSlots().stream()
                        .filter(slot -> targetSlots.contains(new CopySlotKey(block.instanceKey(), slot.slotKey())))
                        .map(slot -> toPromptTargetSlot(block, slot)))
                .sorted(Comparator
                        .comparing((Map<String, Object> slot) -> (Integer) slot.get("blockOrder"),
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(slot -> (Integer) slot.get("slotOrder"),
                                Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private Map<String, Object> toPromptTargetSlot(BlockCopyContext block, TextSlotCopyContext slot) {
        Map<String, Object> promptSlot = new LinkedHashMap<>();
        promptSlot.put("blockOrder", block.blockOrder());
        promptSlot.put("instanceKey", block.instanceKey());
        promptSlot.put("slotKey", slot.slotKey());
        promptSlot.put("slotOrder", slot.slotOrder());
        promptSlot.put("textRole", slot.textRole());
        promptSlot.put("recommendedMaxLength", slot.recommendedMaxLength());
        return promptSlot;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
