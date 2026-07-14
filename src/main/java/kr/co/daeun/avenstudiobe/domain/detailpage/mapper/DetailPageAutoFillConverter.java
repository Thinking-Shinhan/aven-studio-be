package kr.co.daeun.avenstudiobe.domain.detailpage.mapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoImagePlacementReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyGenerateReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyImageSlotReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyTemplateBlockReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageAutoFillMode;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageAutoFillReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageAutoFillTemplateBlockReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ImagePlacementRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ImageSlotReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.TemplateBlockReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.TextSlotReqDto;
import org.springframework.stereotype.Component;

@Component
public class DetailPageAutoFillConverter {

    public AutoImagePlacementReqDto toImagePlacementRequest(DetailPageAutoFillReqDto request) {
        return toImagePlacementRequest(request, resolveMode(request), request.getTargetInstanceKey());
    }

    public AutoImagePlacementReqDto toImagePlacementRequest(
            DetailPageAutoFillReqDto request,
            DetailPageAutoFillMode mode,
            String targetInstanceKey
    ) {
        AutoImagePlacementReqDto converted = new AutoImagePlacementReqDto();
        converted.setProductId(request.getProductId());
        converted.setTemplateBlocks(safeList(request.getTemplateBlocks()).stream()
                .filter(Objects::nonNull)
                .map(block -> toImagePlacementBlock(block, mode, targetInstanceKey))
                .toList());

        return converted;
    }

    public List<DetailPageAutoFillTemplateBlockReqDto> mergeImagePlacements(
            List<DetailPageAutoFillTemplateBlockReqDto> originalBlocks,
            List<ImagePlacementRespDto> placements,
            DetailPageAutoFillMode mode,
            String targetInstanceKey
    ) {
        Map<SlotKey, ImagePlacementRespDto> placementMap = safeList(placements).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        this::toSlotKey,
                        Function.identity(),
                        (first, ignored) -> first
                ));

        return safeList(originalBlocks).stream()
                .filter(Objects::nonNull)
                .map(block -> mergeImagePlacementBlock(block, placementMap, mode, targetInstanceKey))
                .toList();
    }

    public CopyGenerateReqDto toCopyGenerationRequest(
            Long productId,
            List<DetailPageAutoFillTemplateBlockReqDto> mergedBlocks,
            DetailPageAutoFillMode mode,
            String targetInstanceKey
    ) {
        CopyGenerateReqDto converted = new CopyGenerateReqDto();
        converted.setProductId(productId);
        converted.setTemplateBlocks(safeList(mergedBlocks).stream()
                .filter(Objects::nonNull)
                .map(block -> toCopyTemplateBlock(block, mode, targetInstanceKey))
                .toList());

        return converted;
    }

    private TemplateBlockReqDto toImagePlacementBlock(
            DetailPageAutoFillTemplateBlockReqDto block,
            DetailPageAutoFillMode mode,
            String targetInstanceKey
    ) {
        TemplateBlockReqDto converted = new TemplateBlockReqDto();
        converted.setBlockOrder(block.getBlockOrder());
        converted.setInstanceKey(block.getInstanceKey());
        converted.setTemplateKey(block.getTemplateKey());
        converted.setTemplateType(block.getTemplateType());
        converted.setImageSlots(safeList(block.getImageSlots()).stream()
                .filter(Objects::nonNull)
                .map(slot -> toImagePlacementSlot(slot, mode, targetInstanceKey, block.getInstanceKey()))
                .toList());

        return converted;
    }

    private ImageSlotReqDto toImagePlacementSlot(
            ImageSlotReqDto slot,
            DetailPageAutoFillMode mode,
            String targetInstanceKey,
            String instanceKey
    ) {
        ImageSlotReqDto converted = copyImageSlot(slot);
        if (mode == DetailPageAutoFillMode.REGENERATE_ALL
                || isRegenerateTemplateTarget(mode, targetInstanceKey, instanceKey)) {
            converted.setAssetId(null);
        }

        return converted;
    }

    private DetailPageAutoFillTemplateBlockReqDto mergeImagePlacementBlock(
            DetailPageAutoFillTemplateBlockReqDto block,
            Map<SlotKey, ImagePlacementRespDto> placementMap,
            DetailPageAutoFillMode mode,
            String targetInstanceKey
    ) {
        DetailPageAutoFillTemplateBlockReqDto converted = copyBlockWithoutSlots(block);
        converted.setImageSlots(safeList(block.getImageSlots()).stream()
                .filter(Objects::nonNull)
                .map(slot -> mergeImagePlacementSlot(block.getInstanceKey(), slot, placementMap, mode, targetInstanceKey))
                .toList());
        converted.setTextSlots(copyTextSlots(block.getTextSlots()));

        return converted;
    }

    private ImageSlotReqDto mergeImagePlacementSlot(
            String instanceKey,
            ImageSlotReqDto slot,
            Map<SlotKey, ImagePlacementRespDto> placementMap,
            DetailPageAutoFillMode mode,
            String targetInstanceKey
    ) {
        ImageSlotReqDto converted = copyImageSlot(slot);
        ImagePlacementRespDto placement = placementMap.get(toSlotKey(instanceKey, slot.getSlotKey()));

        if (mode == DetailPageAutoFillMode.REGENERATE_TEMPLATE
                && !isRegenerateTemplateTarget(mode, targetInstanceKey, instanceKey)) {
            converted.setAssetId(slot.getAssetId());
        } else {
            converted.setAssetId(placement == null ? null : placement.getAssetId());
        }

        return converted;
    }

    private CopyTemplateBlockReqDto toCopyTemplateBlock(
            DetailPageAutoFillTemplateBlockReqDto block,
            DetailPageAutoFillMode mode,
            String targetInstanceKey
    ) {
        CopyTemplateBlockReqDto converted = new CopyTemplateBlockReqDto();
        converted.setBlockOrder(block.getBlockOrder());
        converted.setInstanceKey(block.getInstanceKey());
        converted.setTemplateKey(block.getTemplateKey());
        converted.setTemplateType(block.getTemplateType());
        converted.setImageSlots(safeList(block.getImageSlots()).stream()
                .filter(Objects::nonNull)
                .filter(slot -> slot.getAssetId() != null)
                .map(this::toCopyImageSlot)
                .toList());
        converted.setTextSlots(safeList(block.getTextSlots()).stream()
                .filter(Objects::nonNull)
                .map(slot -> toCopyTextSlot(slot, mode, targetInstanceKey, block.getInstanceKey()))
                .toList());

        return converted;
    }

    private CopyImageSlotReqDto toCopyImageSlot(ImageSlotReqDto slot) {
        CopyImageSlotReqDto converted = new CopyImageSlotReqDto();
        converted.setSlotKey(slot.getSlotKey());
        converted.setSlotOrder(slot.getSlotOrder());
        converted.setAssetId(slot.getAssetId());

        return converted;
    }

    private DetailPageAutoFillTemplateBlockReqDto copyBlockWithoutSlots(
            DetailPageAutoFillTemplateBlockReqDto block
    ) {
        DetailPageAutoFillTemplateBlockReqDto converted = new DetailPageAutoFillTemplateBlockReqDto();
        converted.setBlockOrder(block.getBlockOrder());
        converted.setInstanceKey(block.getInstanceKey());
        converted.setTemplateKey(block.getTemplateKey());
        converted.setTemplateType(block.getTemplateType());

        return converted;
    }

    private ImageSlotReqDto copyImageSlot(ImageSlotReqDto slot) {
        ImageSlotReqDto converted = new ImageSlotReqDto();
        converted.setSlotKey(slot.getSlotKey());
        converted.setSlotOrder(slot.getSlotOrder());
        converted.setAssetId(slot.getAssetId());

        return converted;
    }

    private List<TextSlotReqDto> copyTextSlots(List<TextSlotReqDto> slots) {
        return safeList(slots).stream()
                .filter(Objects::nonNull)
                .map(slot -> copyTextSlot(slot))
                .toList();
    }

    private TextSlotReqDto toCopyTextSlot(
            TextSlotReqDto slot,
            DetailPageAutoFillMode mode,
            String targetInstanceKey,
            String instanceKey
    ) {
        TextSlotReqDto converted = copyTextSlot(slot);
        if (mode == DetailPageAutoFillMode.REGENERATE_ALL
                || isRegenerateTemplateTarget(mode, targetInstanceKey, instanceKey)) {
            converted.setCurrentContent(null);
        }

        return converted;
    }

    private TextSlotReqDto copyTextSlot(TextSlotReqDto slot) {
        TextSlotReqDto converted = new TextSlotReqDto();
        converted.setSlotKey(slot.getSlotKey());
        converted.setSlotOrder(slot.getSlotOrder());
        converted.setTextRole(slot.getTextRole());
        converted.setRecommendedMaxLength(slot.getRecommendedMaxLength());
        converted.setCurrentContent(slot.getCurrentContent());

        return converted;
    }

    private SlotKey toSlotKey(ImagePlacementRespDto placement) {
        return toSlotKey(placement.getInstanceKey(), placement.getSlotKey());
    }

    private SlotKey toSlotKey(String instanceKey, String slotKey) {
        return new SlotKey(instanceKey, slotKey);
    }

    private DetailPageAutoFillMode resolveMode(DetailPageAutoFillReqDto request) {
        return request.getMode() == null ? DetailPageAutoFillMode.REGENERATE_ALL : request.getMode();
    }

    private boolean isRegenerateTemplateTarget(
            DetailPageAutoFillMode mode,
            String targetInstanceKey,
            String instanceKey
    ) {
        return mode == DetailPageAutoFillMode.REGENERATE_TEMPLATE
                && isSameInstanceKey(targetInstanceKey, instanceKey);
    }

    private boolean isSameInstanceKey(String left, String right) {
        return left != null && left.equals(right);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record SlotKey(String instanceKey, String slotKey) {
    }
}
