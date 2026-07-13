package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import kr.co.daeun.avenstudiobe.domain.detailpage.ai.AutoPlacementAiClient;
import kr.co.daeun.avenstudiobe.domain.detailpage.ai.AutoPlacementAiResponseDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.ai.AutoPlacementPromptBuilder;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoImagePlacementReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoImagePlacementRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoPlacementContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ImagePlacementRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.PlacementSlot;
import kr.co.daeun.avenstudiobe.domain.detailpage.validator.AutoPlacementResultValidator;
import kr.co.daeun.avenstudiobe.global.exception.AutoPlacementInsufficientImageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoImagePlacementService {

    private final AutoPlacementContextLoader contextLoader;
    private final AutoPlacementPromptBuilder promptBuilder;
    private final AutoPlacementAiClient autoPlacementAiClient;
    private final AutoPlacementResultValidator resultValidator;
    private final FallbackPlacementService fallbackPlacementService;

    public AutoImagePlacementRespDto autoPlace(AutoImagePlacementReqDto request) {
        AutoPlacementContext context = contextLoader.load(request);

        if (context.targetSlots().isEmpty()) {
            return AutoImagePlacementRespDto.empty(context.productId());
        }

        if (context.assets().isEmpty()) {
            throw new AutoPlacementInsufficientImageException();
        }

        try {
            String systemPrompt = promptBuilder.buildSystemPrompt();
            String userPrompt = promptBuilder.buildUserPrompt(context);
            AutoPlacementAiResponseDto aiResponse = autoPlacementAiClient.requestPlacement(systemPrompt, userPrompt);

            List<ImagePlacementRespDto> placements =
                    resultValidator.validateAndConvert(aiResponse, context);

            if (!placements.isEmpty()) {
                AutoImagePlacementRespDto fallback =
                        fallbackPlacementService.placeRemaining(context, placements);
                List<ImagePlacementRespDto> mergedPlacements = new ArrayList<>(placements);
                mergedPlacements.addAll(fallback.getPlacements());
                mergedPlacements.sort(Comparator.comparing(
                        placement -> findSlotOrder(context, placement),
                        Comparator.nullsLast(Integer::compareTo)
                ));
                log.info("Auto image placement AI succeeded. productId={}, placementCount={}",
                        context.productId(), mergedPlacements.size());
                return new AutoImagePlacementRespDto(context.productId(), mergedPlacements);
            }

            log.warn("Auto image placement AI returned no valid placements. fallback will be used. productId={}",
                    context.productId());
            return fallbackPlacementService.place(context);
        } catch (Exception e) {
            log.warn("Auto image placement AI flow failed. fallback will be used. productId={}",
                    context.productId(), e);
            return fallbackPlacementService.place(context);
        }
    }

    private Integer findSlotOrder(AutoPlacementContext context, ImagePlacementRespDto placement) {
        return context.slots().stream()
                .filter(slot -> slot.instanceKey().equals(placement.getInstanceKey())
                        && slot.slotKey().equals(placement.getSlotKey()))
                .map(PlacementSlot::globalSlotOrder)
                .findFirst()
                .orElse(null);
    }
}
