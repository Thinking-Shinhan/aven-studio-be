package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AssetCopyContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.BlockCopyContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyImageSlotReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopySlotKey;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyTemplateBlockReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.TextSlotCopyContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.TextSlotReqDto;
import org.springframework.stereotype.Component;

@Component
public class BlockCopyContextBuilder {

    public BuildResult build(
            List<CopyTemplateBlockReqDto> templateBlocks,
            Map<Long, AssetCopyContext> assetMap
    ) {
        return build(templateBlocks, assetMap, null);
    }

    public BuildResult build(
            List<CopyTemplateBlockReqDto> templateBlocks,
            Map<Long, AssetCopyContext> assetMap,
            String targetInstanceKey
    ) {
        List<CopyTemplateBlockReqDto> sortedBlocks = sortBlocks(templateBlocks);
        List<BlockCopyContext> blockContexts = buildBlockContexts(sortedBlocks, assetMap, targetInstanceKey);
        Set<CopySlotKey> targetSlots = collectTargetSlots(blockContexts);

        return new BuildResult(blockContexts, targetSlots);
    }

    private List<CopyTemplateBlockReqDto> sortBlocks(List<CopyTemplateBlockReqDto> blocks) {
        return safeList(blocks).stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        CopyTemplateBlockReqDto::getBlockOrder,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();
    }

    private List<BlockCopyContext> buildBlockContexts(
            List<CopyTemplateBlockReqDto> sortedBlocks,
            Map<Long, AssetCopyContext> assetMap,
            String targetInstanceKey
    ) {
        List<BlockCopyContext> blockContexts = new ArrayList<>();
        List<AssetCopyContext> previousBlockPlacedAssets = List.of();

        for (CopyTemplateBlockReqDto block : sortedBlocks) {
            List<AssetCopyContext> placedAssets = collectPlacedAssets(block, assetMap);
            List<AssetCopyContext> referenceAssets = placedAssets.isEmpty()
                    ? previousBlockPlacedAssets
                    : placedAssets;

            blockContexts.add(new BlockCopyContext(
                    block.getBlockOrder(),
                    block.getInstanceKey(),
                    block.getTemplateKey(),
                    placedAssets,
                    referenceAssets,
                    toTextSlotContexts(block, targetInstanceKey)
            ));

            previousBlockPlacedAssets = placedAssets;
        }

        return blockContexts;
    }

    private List<AssetCopyContext> collectPlacedAssets(
            CopyTemplateBlockReqDto block,
            Map<Long, AssetCopyContext> assetMap
    ) {
        Map<Long, AssetCopyContext> safeAssetMap = assetMap == null ? Map.of() : assetMap;

        return safeList(block.getImageSlots()).stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        CopyImageSlotReqDto::getSlotOrder,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .map(CopyImageSlotReqDto::getAssetId)
                .map(safeAssetMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<TextSlotCopyContext> toTextSlotContexts(
            CopyTemplateBlockReqDto block,
            String targetInstanceKey
    ) {
        return safeList(block.getTextSlots()).stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        TextSlotReqDto::getSlotOrder,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .map(slot -> new TextSlotCopyContext(
                        slot.getSlotKey(),
                        slot.getSlotOrder(),
                        slot.getTextRole(),
                        slot.getRecommendedMaxLength(),
                        slot.getCurrentContent(),
                        isGenerationTarget(block, slot, targetInstanceKey)
                ))
                .toList();
    }

    private Set<CopySlotKey> collectTargetSlots(List<BlockCopyContext> sortedBlocks) {
        Set<CopySlotKey> targetSlots = new HashSet<>();

        for (BlockCopyContext block : sortedBlocks) {
            for (TextSlotCopyContext slot : block.textSlots()) {
                if (slot.generationTarget()) {
                    targetSlots.add(new CopySlotKey(block.instanceKey(), slot.slotKey()));
                }
            }
        }

        return Set.copyOf(targetSlots);
    }

    private boolean isGenerationTarget(
            CopyTemplateBlockReqDto block,
            TextSlotReqDto slot,
            String targetInstanceKey
    ) {
        boolean isTargetBlock = targetInstanceKey == null
                || targetInstanceKey.equals(block.getInstanceKey());
        return isTargetBlock
                && (slot.getCurrentContent() == null || slot.getCurrentContent().isBlank());
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record BuildResult(
            List<BlockCopyContext> sortedBlocks,
            Set<CopySlotKey> targetSlots
    ) {
        public BuildResult {
            sortedBlocks = sortedBlocks == null ? List.of() : sortedBlocks;
            targetSlots = targetSlots == null ? Set.of() : targetSlots;
        }
    }
}
