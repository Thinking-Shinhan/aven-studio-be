package kr.co.daeun.avenstudiobe.domain.detailpage.mapper;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ImageSlotReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.PlacementSlot;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.TemplateBlockReqDto;
import org.springframework.stereotype.Component;

@Component
public class PlacementSlotMapper {

    public List<PlacementSlot> map(List<TemplateBlockReqDto> templateBlocks) {
        if (templateBlocks == null || templateBlocks.isEmpty()) {
            return List.of();
        }

        AtomicInteger globalSlotOrder = new AtomicInteger(1);

        return templateBlocks.stream()
                .filter(block -> !"TEXT".equalsIgnoreCase(block.getTemplateType()))
                .filter(block -> block.getImageSlots() != null && !block.getImageSlots().isEmpty())
                .sorted(Comparator.comparing(
                        TemplateBlockReqDto::getBlockOrder,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .flatMap(block -> block.getImageSlots().stream()
                        .sorted(Comparator.comparing(
                                ImageSlotReqDto::getSlotOrder,
                                Comparator.nullsLast(Integer::compareTo)
                        ))
                        .map(slot -> new PlacementSlot(
                                globalSlotOrder.getAndIncrement(),
                                block.getBlockOrder(),
                                block.getInstanceKey(),
                                block.getTemplateKey(),
                                block.getTemplateType(),
                                slot.getSlotKey(),
                                slot.getSlotOrder(),
                                slot.getAssetId()
                        )))
                .toList();
    }
}
