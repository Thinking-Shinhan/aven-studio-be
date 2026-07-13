package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import java.util.List;
import kr.co.daeun.avenstudiobe.domain.detailpage.ai.CopyGenerator;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyGenerateReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyGenerateRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyGenerationContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyGenerationResult;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyResultDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyWarningDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.validator.CopyGenerateRequestValidator;
import kr.co.daeun.avenstudiobe.domain.detailpage.validator.CopyValidationResult;
import kr.co.daeun.avenstudiobe.domain.detailpage.validator.GeneratedCopyValidator;
import kr.co.daeun.avenstudiobe.global.exception.InvalidCopyGenerationResultException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CopyGenerationService {

    private final CopyGenerateRequestValidator requestValidator;
    private final CopyGenerationContextLoader contextLoader;
    private final CopyGenerator copyGenerator;
    private final GeneratedCopyValidator generatedCopyValidator;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CopyGenerateRespDto generate(CopyGenerateReqDto request, Authentication authentication) {
        requestValidator.validate(request);

        CopyGenerationContext context = contextLoader.load(request, authentication);
        if (context.targetSlots().isEmpty()) {
            return CopyGenerateRespDto.empty(request.getProductId());
        }

        CopyGenerationResult result = copyGenerator.generate(context);
        CopyValidationResult validationResult = generatedCopyValidator.validate(context, result);
        if (!validationResult.isValid() && !validationResult.hasOnlyLengthExceeded()) {
            throw new InvalidCopyGenerationResultException();
        }

        List<CopyResultDto> copies = generatedCopyValidator.sortCopies(context, result.copies());
        List<CopyWarningDto> warnings = generatedCopyValidator.toWarnings(context, result, validationResult);

        return new CopyGenerateRespDto(request.getProductId(), copies, warnings);
    }
}
