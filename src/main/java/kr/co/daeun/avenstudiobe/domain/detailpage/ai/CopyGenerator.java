package kr.co.daeun.avenstudiobe.domain.detailpage.ai;

import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyGenerationContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyGenerationResult;

public interface CopyGenerator {

    CopyGenerationResult generate(CopyGenerationContext context);
}
