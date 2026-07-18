package kr.co.daeun.avenstudiobe.domain.detailpage.validator;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import kr.co.daeun.avenstudiobe.domain.detailpage.ai.AiCopyReviewResponseDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.ai.AiReviewIssueDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto.ReviewTextSlotReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ReviewBlockContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ReviewIssueDto;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CopyReviewResultValidator {

    private final ClaudeProperties claudeProperties;

    public List<ReviewIssueDto> validate(
            CopyReviewContext context,
            AiCopyReviewResponseDto aiResponse
    ) {
        if (context == null || aiResponse == null || aiResponse.issues() == null) {
            throw new IllegalStateException("AI 문구 검수 응답 구조가 올바르지 않습니다.");
        }

        Map<SlotKey, SlotMetadata> slots = buildSlotMap(context);
        Map<SlotKey, ValidatedIssue> bestIssueBySlot = new HashMap<>();

        for (AiReviewIssueDto aiIssue : aiResponse.issues()) {
            if (aiIssue == null) {
                continue;
            }

            SlotKey slotKey = new SlotKey(aiIssue.instanceKey(), aiIssue.slotKey());
            SlotMetadata slot = slots.get(slotKey);
            if (slot == null || isBlank(slot.targetCopy())) {
                continue;
            }

            ReviewCategory category = ReviewCategory.from(aiIssue.category());
            if (category == null) {
                continue;
            }

            if (isBlank(aiIssue.issue())) {
                continue;
            }

            String suggestion = validateSuggestion(aiIssue.suggestion(), slotKey, slot);
            ValidatedIssue candidate = new ValidatedIssue(
                    slot.blockOrder(),
                    slot.slotOrder(),
                    category,
                    new ReviewIssueDto(
                            category.name(),
                            slotKey.instanceKey(),
                            slotKey.slotKey(),
                            slot.textRole(),
                            slot.targetCopy(),
                            aiIssue.issue(),
                            suggestion
                    )
            );
            bestIssueBySlot.merge(
                    slotKey,
                    candidate,
                    (existing, replacement) -> replacement.category().compareTo(existing.category()) < 0
                            ? replacement
                            : existing
            );
        }

        int maxIssues = positiveOrDefault(claudeProperties.getReviewMaxIssues(), 30);
        return bestIssueBySlot.values().stream()
                .sorted(validatedIssueComparator())
                .limit(maxIssues)
                .map(ValidatedIssue::issue)
                .toList();
    }

    private Map<SlotKey, SlotMetadata> buildSlotMap(CopyReviewContext context) {
        Map<SlotKey, SlotMetadata> slots = new HashMap<>();
        for (ReviewBlockContext block : safeList(context.blocks())) {
            if (block == null) {
                continue;
            }
            for (ReviewTextSlotReqDto slot : safeList(block.textSlots())) {
                if (slot == null) {
                    continue;
                }
                slots.putIfAbsent(
                        new SlotKey(block.instanceKey(), slot.slotKey()),
                        new SlotMetadata(
                                block.blockOrder(),
                                slot.slotOrder(),
                                slot.textRole(),
                                slot.currentContent()
                        )
                );
            }
        }
        return slots;
    }

    private String validateSuggestion(
            String suggestion,
            SlotKey slotKey,
            SlotMetadata slot
    ) {
        String invalidReason = null;
        if (isBlank(suggestion)) {
            invalidReason = "blank";
        } else if (Objects.equals(suggestion, slot.targetCopy())) {
            invalidReason = "same_as_target";
        }

        if (invalidReason != null) {
            log.debug(
                    "Copy review suggestion removed. instanceKey={}, slotKey={}, reason={}",
                    slotKey.instanceKey(),
                    slotKey.slotKey(),
                    invalidReason
            );
            return null;
        }
        return suggestion;
    }

    private Comparator<ValidatedIssue> validatedIssueComparator() {
        Comparator<Integer> nullableInteger = Comparator.nullsLast(Integer::compareTo);
        return Comparator
                .comparing(ValidatedIssue::blockOrder, nullableInteger)
                .thenComparing(ValidatedIssue::slotOrder, nullableInteger)
                .thenComparing(ValidatedIssue::category);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private enum ReviewCategory {
        CONTENT_MISMATCH,
        CLAIM_RISK,
        PAGE_COHERENCE,
        BRAND_FIT;

        private static ReviewCategory from(String value) {
            if (value == null) {
                return null;
            }
            try {
                return valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    private record SlotKey(String instanceKey, String slotKey) {
    }

    private record SlotMetadata(
            Integer blockOrder,
            Integer slotOrder,
            String textRole,
            String targetCopy
    ) {
    }

    private record ValidatedIssue(
            Integer blockOrder,
            Integer slotOrder,
            ReviewCategory category,
            ReviewIssueDto issue
    ) {
    }
}
