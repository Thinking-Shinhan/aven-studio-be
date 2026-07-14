package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import java.util.List;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoImagePlacementReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoImagePlacementRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyGenerateReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyGenerateRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyResultDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyWarningDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageAutoFillErrorDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageAutoFillMode;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageAutoFillReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageAutoFillRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageAutoFillStatus;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageAutoFillTemplateBlockReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ImagePlacementRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.mapper.DetailPageAutoFillConverter;
import kr.co.daeun.avenstudiobe.domain.detailpage.validator.DetailPageAutoFillRequestValidator;
import kr.co.daeun.avenstudiobe.domain.product.service.ProductService;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeApiException;
import kr.co.daeun.avenstudiobe.global.exception.InvalidAutoFillRequestException;
import kr.co.daeun.avenstudiobe.global.exception.InvalidCopyGenerationResultException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class DetailPageAutoFillService {

    private final ProductService productService;
    private final AutoImagePlacementService autoImagePlacementService;
    private final CopyGenerationService copyGenerationService;
    private final DetailPageAutoFillConverter converter;
    private final DetailPageAutoFillRequestValidator requestValidator;

    public DetailPageAutoFillRespDto autoFill(DetailPageAutoFillReqDto request, Authentication authentication) {
        requestValidator.validate(request);
        productService.getAccessibleProduct(request.getProductId(), authentication);

        DetailPageAutoFillMode mode = resolveMode(request);
        String targetInstanceKey = resolveTargetInstanceKey(request, mode);
        AutoImagePlacementReqDto imagePlacementRequest =
                converter.toImagePlacementRequest(request);
        List<ImagePlacementRespDto> placements = placeImagesIfNeeded(
                imagePlacementRequest,
                request.getTemplateBlocks(),
                mode,
                targetInstanceKey,
                authentication
        );

        List<DetailPageAutoFillTemplateBlockReqDto> mergedBlocks = converter.mergeImagePlacements(
                request.getTemplateBlocks(),
                placements,
                mode,
                targetInstanceKey
        );
        CopyGenerateReqDto copyGenerationRequest =
                converter.toCopyGenerationRequest(request.getProductId(), mergedBlocks, mode, targetInstanceKey);
        List<ImagePlacementRespDto> responsePlacements = filterPlacements(placements, mode, targetInstanceKey);
        CopyGenerateRespDto copyGenerationResponse;
        try {
            copyGenerationResponse = generateCopyIfNeeded(
                    copyGenerationRequest,
                    request.getTemplateBlocks(),
                    mode,
                    targetInstanceKey,
                    authentication
            );
        } catch (ClaudeApiException e) {
            if (responsePlacements.isEmpty()) {
                throw e;
            }
            return partialSuccess(
                    request.getProductId(),
                    responsePlacements,
                    mode,
                    targetInstanceKey,
                    toCopyAiError(e),
                    e
            );
        } catch (InvalidCopyGenerationResultException e) {
            if (responsePlacements.isEmpty()) {
                throw e;
            }
            return partialSuccess(
                    request.getProductId(),
                    responsePlacements,
                    mode,
                    targetInstanceKey,
                    new DetailPageAutoFillErrorDto(
                            "COPY_GENERATION",
                            "INVALID_COPY_GENERATION_RESULT",
                            true
                    ),
                    e
            );
        }

        return new DetailPageAutoFillRespDto(
                request.getProductId(),
                responsePlacements,
                filterCopies(copyGenerationResponse.getCopies(), mode, targetInstanceKey),
                filterWarnings(copyGenerationResponse.getWarnings(), mode, targetInstanceKey)
        );
    }

    private DetailPageAutoFillRespDto partialSuccess(
            Long productId,
            List<ImagePlacementRespDto> placements,
            DetailPageAutoFillMode mode,
            String targetInstanceKey,
            DetailPageAutoFillErrorDto error,
            RuntimeException cause
    ) {
        log.warn(
                "Detail page auto-fill partially succeeded. productId={}, mode={}, targetInstanceKey={}, errorCode={}",
                productId,
                mode,
                targetInstanceKey,
                error.code(),
                cause
        );
        return new DetailPageAutoFillRespDto(
                productId,
                placements,
                List.of(),
                List.of(),
                DetailPageAutoFillStatus.PARTIAL_SUCCESS,
                List.of(error)
        );
    }

    private DetailPageAutoFillErrorDto toCopyAiError(ClaudeApiException exception) {
        int statusCode = exception.getStatusCode();
        boolean retryable = statusCode == 0
                || statusCode == 408
                || statusCode == 429
                || statusCode >= 500;
        String code = retryable ? "AI_SERVICE_UNAVAILABLE" : "AI_BAD_GATEWAY";

        return new DetailPageAutoFillErrorDto("COPY_GENERATION", code, retryable);
    }

    private List<ImagePlacementRespDto> placeImagesIfNeeded(
            AutoImagePlacementReqDto request,
            List<DetailPageAutoFillTemplateBlockReqDto> originalBlocks,
            DetailPageAutoFillMode mode,
            String targetInstanceKey,
            Authentication authentication
    ) {
        if (!hasImagePlacementTarget(originalBlocks, mode, targetInstanceKey)) {
            return List.of();
        }

        AutoImagePlacementRespDto response =
                autoImagePlacementService.autoPlace(request, authentication, targetInstanceKey);
        return response.getPlacements();
    }

    private CopyGenerateRespDto generateCopyIfNeeded(
            CopyGenerateReqDto request,
            List<DetailPageAutoFillTemplateBlockReqDto> originalBlocks,
            DetailPageAutoFillMode mode,
            String targetInstanceKey,
            Authentication authentication
    ) {
        if (!hasTextGenerationTarget(originalBlocks, mode, targetInstanceKey)) {
            return CopyGenerateRespDto.empty(request.getProductId());
        }

        return copyGenerationService.generate(request, authentication, targetInstanceKey);
    }

    private DetailPageAutoFillMode resolveMode(DetailPageAutoFillReqDto request) {
        DetailPageAutoFillMode mode = request.getMode();
        return mode == null ? DetailPageAutoFillMode.REGENERATE_ALL : mode;
    }

    private String resolveTargetInstanceKey(DetailPageAutoFillReqDto request, DetailPageAutoFillMode mode) {
        if (mode != DetailPageAutoFillMode.REGENERATE_TEMPLATE) {
            return null;
        }

        String targetInstanceKey = request.getTargetInstanceKey();
        if (targetInstanceKey == null || targetInstanceKey.isBlank()) {
            throw new InvalidAutoFillRequestException("지정 템플릿 자동 배치 시 대상 템플릿 식별 키는 필수입니다.");
        }

        boolean exists = safeList(request.getTemplateBlocks()).stream()
                .anyMatch(block -> block != null && targetInstanceKey.equals(block.getInstanceKey()));
        if (!exists) {
            throw new InvalidAutoFillRequestException("대상 템플릿 식별 키와 일치하는 템플릿 블록을 찾을 수 없습니다.");
        }

        return targetInstanceKey;
    }

    private List<ImagePlacementRespDto> filterPlacements(
            List<ImagePlacementRespDto> placements,
            DetailPageAutoFillMode mode,
            String targetInstanceKey
    ) {
        if (mode != DetailPageAutoFillMode.REGENERATE_TEMPLATE) {
            return placements;
        }

        return safeList(placements).stream()
                .filter(placement -> targetInstanceKey.equals(placement.getInstanceKey()))
                .toList();
    }

    private List<CopyResultDto> filterCopies(
            List<CopyResultDto> copies,
            DetailPageAutoFillMode mode,
            String targetInstanceKey
    ) {
        if (mode != DetailPageAutoFillMode.REGENERATE_TEMPLATE) {
            return copies;
        }

        return safeList(copies).stream()
                .filter(copy -> targetInstanceKey.equals(copy.getInstanceKey()))
                .toList();
    }

    private List<CopyWarningDto> filterWarnings(
            List<CopyWarningDto> warnings,
            DetailPageAutoFillMode mode,
            String targetInstanceKey
    ) {
        if (mode != DetailPageAutoFillMode.REGENERATE_TEMPLATE) {
            return warnings;
        }

        return safeList(warnings).stream()
                .filter(warning -> targetInstanceKey.equals(warning.getInstanceKey()))
                .toList();
    }

    private boolean hasImagePlacementTarget(
            List<DetailPageAutoFillTemplateBlockReqDto> blocks,
            DetailPageAutoFillMode mode,
            String targetInstanceKey
    ) {
        return safeList(blocks).stream()
                .filter(block -> block != null && isTargetForMode(block, mode, targetInstanceKey))
                .flatMap(block -> safeList(block.getImageSlots()).stream())
                .findAny()
                .isPresent();
    }

    private boolean hasTextGenerationTarget(
            List<DetailPageAutoFillTemplateBlockReqDto> blocks,
            DetailPageAutoFillMode mode,
            String targetInstanceKey
    ) {
        return safeList(blocks).stream()
                .filter(block -> block != null && isTargetForMode(block, mode, targetInstanceKey))
                .flatMap(block -> safeList(block.getTextSlots()).stream())
                .findAny()
                .isPresent();
    }

    private boolean isTargetForMode(
            DetailPageAutoFillTemplateBlockReqDto block,
            DetailPageAutoFillMode mode,
            String targetInstanceKey
    ) {
        return mode == DetailPageAutoFillMode.REGENERATE_ALL
                || targetInstanceKey.equals(block.getInstanceKey());
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
