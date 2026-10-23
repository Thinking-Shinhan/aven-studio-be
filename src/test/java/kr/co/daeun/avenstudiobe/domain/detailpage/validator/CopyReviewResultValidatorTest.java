package kr.co.daeun.avenstudiobe.domain.detailpage.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import kr.co.daeun.avenstudiobe.domain.detailpage.ai.AiCopyReviewResponseDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.ai.AiReviewIssueDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto.ReviewTextSlotReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ReviewBlockContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ReviewIssueDto;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CopyReviewResultValidatorTest {

    private ClaudeProperties properties;
    private CopyReviewResultValidator validator;

    @BeforeEach
    void setUp() {
        properties = new ClaudeProperties();
        properties.setReviewMaxIssues(30);
        validator = new CopyReviewResultValidator(properties);
    }

    @Test
    void keepsOnlyHighestPriorityIssuePerSlotReinjectsFieldsAndSortsBeforeLimit() {
        properties.setReviewMaxIssues(3);
        CopyReviewContext context = context(List.of(
                block(2, "block-2", slot(1, "slot-2", "BODY", 20, "두 번째 문구")),
                block(1, "block-1", slot(2, "slot-1", "HEADLINE", 20, "첫 번째 문구")),
                block(1, "block-1", slot(1, "slot-0", "EYEBROW", 20, "가장 앞 문구"))
        ));
        AiCopyReviewResponseDto response = new AiCopyReviewResponseDto(List.of(
                issue("BRAND_FIT", "block-2", "slot-2", "브랜드 톤을 확인해요.", "수정 문구"),
                issue("CLAIM_RISK", "missing", "slot", "없는 슬롯이에요.", "수정"),
                issue("UNKNOWN", "block-1", "slot-1", "잘못된 분류예요.", "수정"),
                issue("CLAIM_RISK", "block-1", "slot-1", "근거가 부족해요.", "첫 번째 문구"),
                issue("BRAND_FIT", "block-1", "slot-0", "톤이 어울리지 않아요.", "차분한 문구"),
                issue("CONTENT_MISMATCH", "block-1", "slot-0", "상품 정보와 달라요.", "길이가 스무 자를 훨씬 넘는 매우 긴 제안 문구예요."),
                issue("CONTENT_MISMATCH", "block-1", "slot-0", "중복 이슈예요.", "다른 제안")
        ));

        List<ReviewIssueDto> issues = validator.validate(context, response);

        assertEquals(3, issues.size());
        assertEquals("CONTENT_MISMATCH", issues.get(0).category());
        assertEquals("CLAIM_RISK", issues.get(1).category());
        assertEquals("BRAND_FIT", issues.get(2).category());
        assertEquals("block-1", issues.get(0).instanceKey());
        assertEquals("slot-0", issues.get(0).slotKey());
        assertEquals("상품 정보와 달라요.", issues.get(0).issue());
        assertEquals("가장 앞 문구", issues.get(0).targetCopy());
        assertEquals("EYEBROW", issues.get(0).textRole());
        assertEquals("길이가 스무 자를 훨씬 넘는 매우 긴 제안 문구예요.", issues.get(0).suggestion());
        assertNull(issues.get(1).suggestion());
    }

    @Test
    void keepsIssueButClearsBlankSuggestion() {
        CopyReviewContext context = context(List.of(
                block(1, "block-1", slot(1, "slot-1", "BODY", 20, "원문"))
        ));

        List<ReviewIssueDto> issues = validator.validate(context, new AiCopyReviewResponseDto(List.of(
                issue("CLAIM_RISK", "block-1", "slot-1", "근거가 부족해요.", " ")
        )));

        assertEquals(1, issues.size());
        assertNull(issues.get(0).suggestion());
    }

    @Test
    void removesBlankTargetAndBlankIssue() {
        CopyReviewContext context = context(List.of(
                block(1, "block-1", slot(1, "blank", "BODY", 20, " ")),
                block(2, "block-2", slot(1, "valid", "BODY", 20, "원문"))
        ));
        AiCopyReviewResponseDto response = new AiCopyReviewResponseDto(List.of(
                issue("CLAIM_RISK", "block-1", "blank", "문제가 있어요.", "수정"),
                issue("CLAIM_RISK", "block-2", "valid", " ", "수정")
        ));

        assertEquals(List.of(), validator.validate(context, response));
    }

    @Test
    void rejectsMissingIssuesArrayAsInvalidStructure() {
        assertThrows(
                IllegalStateException.class,
                () -> validator.validate(context(List.of()), new AiCopyReviewResponseDto(null))
        );
    }

    private CopyReviewContext context(List<ReviewBlockContext> blocks) {
        return new CopyReviewContext(null, null, blocks, List.of());
    }

    private ReviewBlockContext block(
            int blockOrder,
            String instanceKey,
            ReviewTextSlotReqDto slot
    ) {
        return new ReviewBlockContext(
                blockOrder,
                instanceKey,
                "template",
                "TEXT",
                List.of(slot),
                List.of(),
                List.of(),
                false
        );
    }

    private ReviewTextSlotReqDto slot(
            int slotOrder,
            String slotKey,
            String textRole,
            int maxLength,
            String content
    ) {
        return new ReviewTextSlotReqDto(slotKey, slotOrder, textRole, maxLength, content);
    }

    private AiReviewIssueDto issue(
            String category,
            String instanceKey,
            String slotKey,
            String issue,
            String suggestion
    ) {
        return new AiReviewIssueDto(category, instanceKey, slotKey, issue, suggestion);
    }
}
