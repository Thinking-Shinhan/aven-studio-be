package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import kr.co.daeun.avenstudiobe.domain.detailpage.ai.AiCopyReviewResponseDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.ai.ClaudeCopyReviewer;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto.ReviewTextSlotReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ReviewBlockContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ReviewIssueDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.service.ReviewImageProcessor.ProcessResult;
import kr.co.daeun.avenstudiobe.domain.detailpage.validator.CopyReviewRequestValidator;
import kr.co.daeun.avenstudiobe.domain.detailpage.validator.CopyReviewResultValidator;
import kr.co.daeun.avenstudiobe.global.exception.ProductNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class CopyReviewServiceTest {

    private CopyReviewRequestValidator requestValidator;
    private CopyReviewContextLoader contextLoader;
    private ReviewImageProcessor imageProcessor;
    private ClaudeCopyReviewer copyReviewer;
    private CopyReviewResultValidator resultValidator;
    private CopyReviewService service;
    private Authentication authentication;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        requestValidator = mock(CopyReviewRequestValidator.class);
        contextLoader = mock(CopyReviewContextLoader.class);
        imageProcessor = mock(ReviewImageProcessor.class);
        copyReviewer = mock(ClaudeCopyReviewer.class);
        resultValidator = mock(CopyReviewResultValidator.class);
        meterRegistry = new SimpleMeterRegistry();
        service = new CopyReviewService(
                requestValidator,
                contextLoader,
                imageProcessor,
                copyReviewer,
                resultValidator,
                meterRegistry
        );
        authentication = mock(Authentication.class);
    }

    @Test
    void assemblesReviewFlowInOrder() {
        CopyReviewReqDto request = new CopyReviewReqDto(14L, List.of());
        CopyReviewContext loaded = contextWithTarget();
        ProcessResult processed = new ProcessResult(loaded.blocks(), List.of());
        AiCopyReviewResponseDto aiResponse = new AiCopyReviewResponseDto(List.of());
        ReviewIssueDto issue = new ReviewIssueDto(
                "CLAIM_RISK", "block", "slot", "BODY", "원문", "문제가 있어요.", null
        );
        when(contextLoader.load(request, authentication)).thenReturn(loaded);
        when(imageProcessor.process(loaded.blocks(), loaded.assetSources())).thenReturn(processed);
        when(copyReviewer.review(loaded, processed.images())).thenReturn(aiResponse);
        when(resultValidator.validate(loaded, aiResponse)).thenReturn(List.of(issue));

        CopyReviewRespDto response = service.review(request, authentication);

        assertEquals(14L, response.productId());
        assertEquals(List.of(issue), response.issues());
        InOrder order = inOrder(requestValidator, contextLoader, imageProcessor, copyReviewer, resultValidator);
        order.verify(requestValidator).validate(request);
        order.verify(contextLoader).load(request, authentication);
        order.verify(imageProcessor).process(loaded.blocks(), loaded.assetSources());
        order.verify(copyReviewer).review(loaded, processed.images());
        order.verify(resultValidator).validate(loaded, aiResponse);
        assertEquals(1, meterRegistry.timer("review.total").count());
        assertEquals(1, meterRegistry.timer("review.image.pipeline").count());
        assertEquals(1, meterRegistry.timer("review.claude.call").count());
    }

    @Test
    void returnsEmptySuccessWithoutImageOrClaudeCallWhenNoReviewTargetExists() {
        CopyReviewReqDto request = new CopyReviewReqDto(14L, List.of());
        CopyReviewContext context = new CopyReviewContext(null, null, List.of(), List.of());
        when(contextLoader.load(request, authentication)).thenReturn(context);

        CopyReviewRespDto response = service.review(request, authentication);

        assertEquals(14L, response.productId());
        assertEquals(List.of(), response.issues());
        verify(imageProcessor, never()).process(context.blocks(), context.assetSources());
        verify(copyReviewer, never()).review(context, List.of());
    }

    @Test
    void mapsInaccessibleProductToBadRequestExceptionWithoutChangingGlobalProductMapping() {
        CopyReviewReqDto request = new CopyReviewReqDto(14L, List.of());
        when(contextLoader.load(request, authentication)).thenThrow(new ProductNotFoundException());

        assertThrows(IllegalArgumentException.class, () -> service.review(request, authentication));
        verifyNoInteractions(imageProcessor, copyReviewer, resultValidator);
    }

    @Test
    void suspendsTransactionsForTheWholeReviewFlow() throws NoSuchMethodException {
        Transactional transactional = CopyReviewService.class
                .getMethod("review", CopyReviewReqDto.class, Authentication.class)
                .getAnnotation(Transactional.class);

        assertEquals(Propagation.NOT_SUPPORTED, transactional.propagation());
    }

    private CopyReviewContext contextWithTarget() {
        ReviewTextSlotReqDto slot = new ReviewTextSlotReqDto("slot", 1, "BODY", 20, "원문");
        ReviewBlockContext block = new ReviewBlockContext(
                1, "block", "template", "TEXT", List.of(slot), List.of(), List.of(), false
        );
        return new CopyReviewContext(null, null, List.of(block), List.of());
    }
}
