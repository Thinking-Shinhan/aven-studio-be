package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoImagePlacementReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoImagePlacementRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyGenerateReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyGenerateRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyResultDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageAutoFillMode;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageAutoFillReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageAutoFillRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageAutoFillStatus;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageAutoFillTemplateBlockReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ImagePlacementRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ImageSlotReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.TextSlotReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.mapper.DetailPageAutoFillConverter;
import kr.co.daeun.avenstudiobe.domain.detailpage.validator.DetailPageAutoFillRequestValidator;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.service.ProductService;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeApiException;
import kr.co.daeun.avenstudiobe.global.exception.InvalidAutoFillRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;

class DetailPageAutoFillServiceTest {

    private ProductService productService;
    private AutoImagePlacementService autoImagePlacementService;
    private CopyGenerationService copyGenerationService;
    private DetailPageAutoFillService service;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        autoImagePlacementService = mock(AutoImagePlacementService.class);
        copyGenerationService = mock(CopyGenerationService.class);
        service = new DetailPageAutoFillService(
                productService,
                autoImagePlacementService,
                copyGenerationService,
                new DetailPageAutoFillConverter(),
                new DetailPageAutoFillRequestValidator()
        );
        authentication = mock(Authentication.class);

        when(productService.getAccessibleProduct(anyLong(), any(Authentication.class)))
                .thenReturn(mock(Product.class));
    }

    @Test
    void regenerateAllIgnoresExistingImageAssetIdsAndReplacesAllImageSlots() {
        DetailPageAutoFillReqDto request = request(
                DetailPageAutoFillMode.REGENERATE_ALL,
                null,
                block("hero", List.of(imageSlot("image-1", 11L)), List.of()),
                block("detail", List.of(imageSlot("image-2", 22L)), List.of())
        );
        when(autoImagePlacementService.autoPlace(any(), any(), nullable(String.class)))
                .thenReturn(new AutoImagePlacementRespDto(
                1L,
                List.of(
                        placement("hero", "image-1", 101L),
                        placement("detail", "image-2", 102L)
                )
        ));

        DetailPageAutoFillRespDto response = service.autoFill(request, authentication);

        ArgumentCaptor<AutoImagePlacementReqDto> captor = ArgumentCaptor.forClass(AutoImagePlacementReqDto.class);
        verify(autoImagePlacementService).autoPlace(captor.capture(), any(), nullable(String.class));
        assertNull(captor.getValue().getTemplateBlocks().get(0).getImageSlots().get(0).getAssetId());
        assertNull(captor.getValue().getTemplateBlocks().get(1).getImageSlots().get(0).getAssetId());
        assertEquals(List.of(101L, 102L), response.getPlacements().stream()
                .map(ImagePlacementRespDto::getAssetId)
                .toList());
        assertEquals(DetailPageAutoFillStatus.SUCCESS, response.getStatus());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    void regenerateAllIgnoresExistingTextContentAndRegeneratesAllTextSlots() {
        DetailPageAutoFillReqDto request = request(
                DetailPageAutoFillMode.REGENERATE_ALL,
                null,
                block("hero", List.of(), List.of(textSlot("headline", "Old headline"))),
                block("detail", List.of(), List.of(textSlot("body", "Old body")))
        );
        when(copyGenerationService.generate(any(), any(), nullable(String.class)))
                .thenReturn(new CopyGenerateRespDto(
                1L,
                List.of(
                        copy("hero", "headline", "New headline"),
                        copy("detail", "body", "New body")
                ),
                List.of()
        ));

        DetailPageAutoFillRespDto response = service.autoFill(request, authentication);

        ArgumentCaptor<CopyGenerateReqDto> captor = ArgumentCaptor.forClass(CopyGenerateReqDto.class);
        verify(autoImagePlacementService, never()).autoPlace(any(), any(), nullable(String.class));
        verify(copyGenerationService).generate(captor.capture(), any(), nullable(String.class));
        assertNull(captor.getValue().getTemplateBlocks().get(0).getTextSlots().get(0).getCurrentContent());
        assertNull(captor.getValue().getTemplateBlocks().get(1).getTextSlots().get(0).getCurrentContent());
        assertEquals(2, response.getCopies().size());
    }

    @Test
    void regenerateTemplatePlacesImagesOnlyForTargetBlock() {
        DetailPageAutoFillReqDto request = request(
                DetailPageAutoFillMode.REGENERATE_TEMPLATE,
                "hero",
                block("hero", List.of(imageSlot("image-1", 11L)), List.of()),
                block("detail", List.of(imageSlot("image-2", 22L)), List.of())
        );
        when(autoImagePlacementService.autoPlace(any(), any(), nullable(String.class)))
                .thenReturn(new AutoImagePlacementRespDto(
                1L,
                List.of(
                        placement("hero", "image-1", 101L),
                        placement("detail", "image-2", 102L)
                )
        ));

        DetailPageAutoFillRespDto response = service.autoFill(request, authentication);

        ArgumentCaptor<AutoImagePlacementReqDto> captor = ArgumentCaptor.forClass(AutoImagePlacementReqDto.class);
        verify(autoImagePlacementService).autoPlace(captor.capture(), any(), nullable(String.class));
        assertNull(captor.getValue().getTemplateBlocks().get(0).getImageSlots().get(0).getAssetId());
        assertEquals(22L, captor.getValue().getTemplateBlocks().get(1).getImageSlots().get(0).getAssetId());
        assertEquals(1, response.getPlacements().size());
        assertEquals("hero", response.getPlacements().get(0).getInstanceKey());
    }

    @Test
    void regenerateTemplateKeepsNonTargetImagesAsCopyContext() {
        DetailPageAutoFillReqDto request = request(
                DetailPageAutoFillMode.REGENERATE_TEMPLATE,
                "hero",
                block("hero", List.of(imageSlot("image-1", 11L)), List.of(textSlot("headline", "Old"))),
                block("detail", List.of(imageSlot("image-2", 22L)), List.of(textSlot("body", "Context")))
        );
        when(autoImagePlacementService.autoPlace(any(), any(), nullable(String.class)))
                .thenReturn(new AutoImagePlacementRespDto(
                1L,
                List.of(placement("hero", "image-1", 101L))
        ));
        when(copyGenerationService.generate(any(), any(), nullable(String.class)))
                .thenReturn(new CopyGenerateRespDto(
                1L,
                List.of(copy("hero", "headline", "New")),
                List.of()
        ));

        service.autoFill(request, authentication);

        ArgumentCaptor<CopyGenerateReqDto> captor = ArgumentCaptor.forClass(CopyGenerateReqDto.class);
        verify(copyGenerationService).generate(captor.capture(), any(), nullable(String.class));
        assertEquals(101L, captor.getValue().getTemplateBlocks().get(0).getImageSlots().get(0).getAssetId());
        assertEquals(22L, captor.getValue().getTemplateBlocks().get(1).getImageSlots().get(0).getAssetId());
    }

    @Test
    void regenerateTemplateRegeneratesOnlyTargetTextAndKeepsNonTargetTextAsContext() {
        DetailPageAutoFillReqDto request = request(
                DetailPageAutoFillMode.REGENERATE_TEMPLATE,
                "hero",
                block("hero", List.of(), List.of(textSlot("headline", "Old headline"))),
                block("detail", List.of(), List.of(textSlot("body", "Existing body")))
        );
        when(copyGenerationService.generate(any(), any(), nullable(String.class)))
                .thenReturn(new CopyGenerateRespDto(
                1L,
                List.of(
                        copy("hero", "headline", "New headline"),
                        copy("detail", "body", "Should be filtered")
                ),
                List.of()
        ));

        DetailPageAutoFillRespDto response = service.autoFill(request, authentication);

        ArgumentCaptor<CopyGenerateReqDto> captor = ArgumentCaptor.forClass(CopyGenerateReqDto.class);
        verify(copyGenerationService).generate(captor.capture(), any(), nullable(String.class));
        assertNull(captor.getValue().getTemplateBlocks().get(0).getTextSlots().get(0).getCurrentContent());
        assertEquals("Existing body",
                captor.getValue().getTemplateBlocks().get(1).getTextSlots().get(0).getCurrentContent());
        assertEquals(1, response.getCopies().size());
        assertEquals("hero", response.getCopies().get(0).getInstanceKey());
    }

    @Test
    void regenerateTemplateRequiresValidTargetInstanceKey() {
        DetailPageAutoFillReqDto nullTarget = request(
                DetailPageAutoFillMode.REGENERATE_TEMPLATE,
                null,
                block("hero", List.of(imageSlot("image-1", 11L)), List.of())
        );
        DetailPageAutoFillReqDto missingTarget = request(
                DetailPageAutoFillMode.REGENERATE_TEMPLATE,
                "missing",
                block("hero", List.of(imageSlot("image-1", 11L)), List.of())
        );

        InvalidAutoFillRequestException nullTargetException = assertThrows(
                InvalidAutoFillRequestException.class,
                () -> service.autoFill(nullTarget, authentication)
        );
        InvalidAutoFillRequestException missingTargetException = assertThrows(
                InvalidAutoFillRequestException.class,
                () -> service.autoFill(missingTarget, authentication)
        );

        assertEquals(
                "지정 템플릿 자동 배치 시 대상 템플릿 식별 키는 필수입니다.",
                nullTargetException.getMessage()
        );
        assertEquals(
                "대상 템플릿 식별 키와 일치하는 템플릿 블록을 찾을 수 없습니다.",
                missingTargetException.getMessage()
        );
    }

    @Test
    void regenerateTemplateTextOnlyTargetGeneratesCopyWithoutImagePlacement() {
        DetailPageAutoFillReqDto request = request(
                DetailPageAutoFillMode.REGENERATE_TEMPLATE,
                "hero",
                block("hero", List.of(), List.of(textSlot("headline", "Old headline")))
        );
        when(copyGenerationService.generate(any(), any(), nullable(String.class)))
                .thenReturn(new CopyGenerateRespDto(
                1L,
                List.of(copy("hero", "headline", "New headline")),
                List.of()
        ));

        DetailPageAutoFillRespDto response = service.autoFill(request, authentication);

        verify(autoImagePlacementService, never()).autoPlace(any(), any(), nullable(String.class));
        verify(copyGenerationService).generate(any(), any(), nullable(String.class));
        assertEquals(1, response.getCopies().size());
        assertEquals(0, response.getPlacements().size());
    }

    @Test
    void regenerateTemplateImageOnlyTargetPlacesImageWithoutCopyGeneration() {
        DetailPageAutoFillReqDto request = request(
                DetailPageAutoFillMode.REGENERATE_TEMPLATE,
                "hero",
                block("hero", List.of(imageSlot("image-1", 11L)), List.of())
        );
        when(autoImagePlacementService.autoPlace(any(), any(), nullable(String.class)))
                .thenReturn(new AutoImagePlacementRespDto(
                1L,
                List.of(placement("hero", "image-1", 101L))
        ));

        DetailPageAutoFillRespDto response = service.autoFill(request, authentication);

        verify(autoImagePlacementService).autoPlace(any(), any(), nullable(String.class));
        verify(copyGenerationService, never()).generate(any(), any(), nullable(String.class));
        assertEquals(1, response.getPlacements().size());
        assertEquals(0, response.getCopies().size());
    }

    @Test
    void returnsPlacedImagesAsPartialSuccessWhenCopyAiIsUnavailable() {
        DetailPageAutoFillReqDto request = request(
                DetailPageAutoFillMode.REGENERATE_ALL,
                null,
                block(
                        "hero",
                        List.of(imageSlot("image-1", null)),
                        List.of(textSlot("headline", null))
                )
        );
        when(autoImagePlacementService.autoPlace(any(), any(), nullable(String.class)))
                .thenReturn(new AutoImagePlacementRespDto(
                        1L,
                        List.of(placement("hero", "image-1", 101L))
                ));
        when(copyGenerationService.generate(any(), any(), nullable(String.class)))
                .thenThrow(new ClaudeApiException("Claude request failed."));

        DetailPageAutoFillRespDto response = service.autoFill(request, authentication);

        assertEquals(DetailPageAutoFillStatus.PARTIAL_SUCCESS, response.getStatus());
        assertEquals(1, response.getPlacements().size());
        assertEquals(0, response.getCopies().size());
        assertEquals(1, response.getErrors().size());
        assertEquals("COPY_GENERATION", response.getErrors().get(0).stage());
        assertEquals("AI_SERVICE_UNAVAILABLE", response.getErrors().get(0).code());
        assertEquals(true, response.getErrors().get(0).retryable());
    }

    @Test
    void keepsCopyFailureAsAnErrorWhenThereIsNoSuccessfulImageResult() {
        DetailPageAutoFillReqDto request = request(
                DetailPageAutoFillMode.REGENERATE_ALL,
                null,
                block("hero", List.of(), List.of(textSlot("headline", null)))
        );
        when(copyGenerationService.generate(any(), any(), nullable(String.class)))
                .thenThrow(new ClaudeApiException("Claude request failed."));

        assertThrows(ClaudeApiException.class, () -> service.autoFill(request, authentication));
    }

    private DetailPageAutoFillReqDto request(
            DetailPageAutoFillMode mode,
            String targetInstanceKey,
            DetailPageAutoFillTemplateBlockReqDto... blocks
    ) {
        DetailPageAutoFillReqDto request = new DetailPageAutoFillReqDto();
        request.setProductId(1L);
        request.setMode(mode);
        request.setTargetInstanceKey(targetInstanceKey);
        request.setTemplateBlocks(List.of(blocks));
        return request;
    }

    private DetailPageAutoFillTemplateBlockReqDto block(
            String instanceKey,
            List<ImageSlotReqDto> imageSlots,
            List<TextSlotReqDto> textSlots
    ) {
        DetailPageAutoFillTemplateBlockReqDto block = new DetailPageAutoFillTemplateBlockReqDto();
        block.setBlockOrder(instanceKey.equals("hero") ? 1 : 2);
        block.setInstanceKey(instanceKey);
        block.setTemplateKey("template-" + instanceKey);
        block.setTemplateType(imageSlots.isEmpty() ? "TEXT" : textSlots.isEmpty() ? "IMAGE" : "MIXED");
        block.setImageSlots(imageSlots);
        block.setTextSlots(textSlots);
        return block;
    }

    private ImageSlotReqDto imageSlot(String slotKey, Long assetId) {
        ImageSlotReqDto slot = new ImageSlotReqDto();
        slot.setSlotKey(slotKey);
        slot.setSlotOrder(1);
        slot.setAssetId(assetId);
        return slot;
    }

    private TextSlotReqDto textSlot(String slotKey, String currentContent) {
        TextSlotReqDto slot = new TextSlotReqDto();
        slot.setSlotKey(slotKey);
        slot.setSlotOrder(1);
        slot.setTextRole("HEADLINE");
        slot.setRecommendedMaxLength(30);
        slot.setCurrentContent(currentContent);
        return slot;
    }

    private ImagePlacementRespDto placement(String instanceKey, String slotKey, Long assetId) {
        return new ImagePlacementRespDto(instanceKey, slotKey, assetId, "https://example.com/" + assetId, "FRONT_VIEW");
    }

    private CopyResultDto copy(String instanceKey, String slotKey, String content) {
        return new CopyResultDto(instanceKey, slotKey, content);
    }
}
