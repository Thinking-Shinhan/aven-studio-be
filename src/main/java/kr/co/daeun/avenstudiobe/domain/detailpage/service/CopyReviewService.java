package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import java.util.List;
import kr.co.daeun.avenstudiobe.domain.detailpage.ai.AiCopyReviewResponseDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.ai.ClaudeCopyReviewer;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ReviewIssueDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.service.ReviewImageProcessor.ProcessResult;
import kr.co.daeun.avenstudiobe.domain.detailpage.validator.CopyReviewRequestValidator;
import kr.co.daeun.avenstudiobe.domain.detailpage.validator.CopyReviewResultValidator;
import kr.co.daeun.avenstudiobe.global.exception.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CopyReviewService {

    private final CopyReviewRequestValidator requestValidator;
    private final CopyReviewContextLoader contextLoader;
    private final ReviewImageProcessor imageProcessor;
    private final ClaudeCopyReviewer copyReviewer;
    private final CopyReviewResultValidator resultValidator;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CopyReviewRespDto review(CopyReviewReqDto request, Authentication authentication) {
        requestValidator.validate(request);

        CopyReviewContext loadedContext;
        try {
            loadedContext = contextLoader.load(request, authentication);
        } catch (ProductNotFoundException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }

        if (hasNoReviewTargets(loadedContext)) {
            return new CopyReviewRespDto(request.productId(), List.of());
        }

        ProcessResult processed = imageProcessor.process(
                loadedContext.blocks(),
                loadedContext.assetSources()
        );
        CopyReviewContext reviewContext = new CopyReviewContext(
                loadedContext.product(),
                loadedContext.brand(),
                processed.blocks(),
                loadedContext.assetSources()
        );

        AiCopyReviewResponseDto aiResponse = copyReviewer.review(reviewContext, processed.images());
        List<ReviewIssueDto> issues = resultValidator.validate(reviewContext, aiResponse);
        return new CopyReviewRespDto(request.productId(), issues);
    }

    private boolean hasNoReviewTargets(CopyReviewContext context) {
        return context == null || context.blocks() == null || context.blocks().stream()
                .filter(block -> block != null && block.textSlots() != null)
                .allMatch(block -> block.textSlots().isEmpty());
    }
}
