package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import java.util.List;
import kr.co.daeun.avenstudiobe.domain.detailpage.ai.AutoPlacementAiClient;
import kr.co.daeun.avenstudiobe.domain.detailpage.ai.AutoPlacementAiResponseDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.ai.AutoPlacementPromptBuilder;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoImagePlacementReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoImagePlacementRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoPlacementContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ImagePlacementRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.validator.AutoPlacementResultValidator;
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

        if (context.assets().isEmpty() || context.slots().isEmpty()) {
            return AutoImagePlacementRespDto.empty(context.productId());
        }

        try {
            String systemPrompt = promptBuilder.buildSystemPrompt();
            String userPrompt = promptBuilder.buildUserPrompt(context);
            AutoPlacementAiResponseDto aiResponse = autoPlacementAiClient.requestPlacement(systemPrompt, userPrompt);

            List<ImagePlacementRespDto> placements =
                    resultValidator.validateAndConvert(aiResponse, context);

            if (!placements.isEmpty()) {
                return new AutoImagePlacementRespDto(context.productId(), placements);
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
}
