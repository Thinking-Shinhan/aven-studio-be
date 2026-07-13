package kr.co.daeun.avenstudiobe.domain.detailpage.validator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.BlockCopyContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyGenerationContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyGenerationResult;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyResultDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopySlotKey;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyWarningDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.TextSlotCopyContext;
import org.springframework.stereotype.Component;

@Component
public class GeneratedCopyValidator {

    private static final String LENGTH_WARNING_CODE = "RECOMMENDED_LENGTH_EXCEEDED";
    private static final String LENGTH_WARNING_MESSAGE = "권장 최대 길이를 초과했습니다.";

    public CopyValidationResult validate(CopyGenerationContext context, CopyGenerationResult result) {
        Map<CopySlotKey, SlotOrder> targetSlotMap = buildTargetSlotMap(context);
        List<CopyResultDto> copies = safeCopies(result);
        List<CopyValidationError> errors = new ArrayList<>();

        validateMissingSlots(targetSlotMap, copies, errors);
        validateUnknownSlots(targetSlotMap, copies, errors);
        validateDuplicateSlots(copies, errors);
        validateEmptyContents(copies, errors);
        validateLengthExceeded(targetSlotMap, copies, errors);

        return new CopyValidationResult(errors);
    }

    public List<CopyWarningDto> toWarnings(
            CopyGenerationContext context,
            CopyGenerationResult result,
            CopyValidationResult validationResult
    ) {
        Map<CopySlotKey, SlotOrder> targetSlotMap = buildTargetSlotMap(context);
        Map<CopySlotKey, CopyResultDto> copyMap = safeCopies(result).stream()
                .collect(Collectors.toMap(
                        this::toKey,
                        Function.identity(),
                        (first, ignored) -> first
                ));

        List<CopyWarningDto> warnings = safeErrors(validationResult).stream()
                .filter(error -> error.code() == CopyValidationErrorCode.LENGTH_EXCEEDED)
                .map(error -> toWarning(error, targetSlotMap, copyMap))
                .toList();

        return sortWarnings(context, warnings);
    }

    public List<CopyResultDto> sortCopies(CopyGenerationContext context, List<CopyResultDto> copies) {
        Map<CopySlotKey, SlotOrder> slotOrderMap = buildTargetSlotMap(context);

        return safeList(copies).stream()
                .sorted(Comparator.comparing(
                        copy -> slotOrderMap.get(toKey(copy)),
                        Comparator.nullsLast(SlotOrder::compareTo)
                ))
                .toList();
    }

    public List<CopyWarningDto> sortWarnings(CopyGenerationContext context, List<CopyWarningDto> warnings) {
        Map<CopySlotKey, SlotOrder> slotOrderMap = buildTargetSlotMap(context);

        return safeList(warnings).stream()
                .sorted(Comparator.comparing(
                        warning -> slotOrderMap.get(new CopySlotKey(warning.getInstanceKey(), warning.getSlotKey())),
                        Comparator.nullsLast(SlotOrder::compareTo)
                ))
                .toList();
    }

    private void validateMissingSlots(
            Map<CopySlotKey, SlotOrder> targetSlotMap,
            List<CopyResultDto> copies,
            List<CopyValidationError> errors
    ) {
        Set<CopySlotKey> returnedSlots = copies.stream()
                .map(this::toKey)
                .collect(Collectors.toSet());

        for (CopySlotKey targetSlot : targetSlotMap.keySet()) {
            if (!returnedSlots.contains(targetSlot)) {
                errors.add(new CopyValidationError(
                        CopyValidationErrorCode.MISSING_SLOT,
                        targetSlot.instanceKey(),
                        targetSlot.slotKey(),
                        "생성 대상 슬롯이 응답에서 누락되었습니다."
                ));
            }
        }
    }

    private void validateUnknownSlots(
            Map<CopySlotKey, SlotOrder> targetSlotMap,
            List<CopyResultDto> copies,
            List<CopyValidationError> errors
    ) {
        for (CopyResultDto copy : copies) {
            CopySlotKey key = toKey(copy);
            if (!targetSlotMap.containsKey(key)) {
                errors.add(new CopyValidationError(
                        CopyValidationErrorCode.UNKNOWN_SLOT,
                        key.instanceKey(),
                        key.slotKey(),
                        "요청에 없는 생성 대상 슬롯입니다."
                ));
            }
        }
    }

    private void validateDuplicateSlots(
            List<CopyResultDto> copies,
            List<CopyValidationError> errors
    ) {
        Set<CopySlotKey> seen = new HashSet<>();

        for (CopyResultDto copy : copies) {
            CopySlotKey key = toKey(copy);
            if (!seen.add(key)) {
                errors.add(new CopyValidationError(
                        CopyValidationErrorCode.DUPLICATE_SLOT,
                        key.instanceKey(),
                        key.slotKey(),
                        "동일 슬롯이 중복 반환되었습니다."
                ));
            }
        }
    }

    private void validateEmptyContents(
            List<CopyResultDto> copies,
            List<CopyValidationError> errors
    ) {
        for (CopyResultDto copy : copies) {
            if (copy == null || isBlank(copy.getContent())) {
                CopySlotKey key = toKey(copy);
                errors.add(new CopyValidationError(
                        CopyValidationErrorCode.EMPTY_CONTENT,
                        key.instanceKey(),
                        key.slotKey(),
                        "content가 비어 있습니다."
                ));
            }
        }
    }

    private void validateLengthExceeded(
            Map<CopySlotKey, SlotOrder> targetSlotMap,
            List<CopyResultDto> copies,
            List<CopyValidationError> errors
    ) {
        for (CopyResultDto copy : copies) {
            CopySlotKey key = toKey(copy);
            SlotOrder slotOrder = targetSlotMap.get(key);
            if (copy == null || slotOrder == null || slotOrder.recommendedMaxLength() == null) {
                continue;
            }

            int actualLength = copy.getContent() == null ? 0 : copy.getContent().length();
            if (actualLength > slotOrder.recommendedMaxLength()) {
                errors.add(new CopyValidationError(
                        CopyValidationErrorCode.LENGTH_EXCEEDED,
                        key.instanceKey(),
                        key.slotKey(),
                        "content 길이가 recommendedMaxLength를 초과했습니다."
                ));
            }
        }
    }

    private CopyWarningDto toWarning(
            CopyValidationError error,
            Map<CopySlotKey, SlotOrder> targetSlotMap,
            Map<CopySlotKey, CopyResultDto> copyMap
    ) {
        CopySlotKey key = new CopySlotKey(error.instanceKey(), error.slotKey());
        SlotOrder slotOrder = targetSlotMap.get(key);
        CopyResultDto copy = copyMap.get(key);
        String content = copy == null ? null : copy.getContent();

        return new CopyWarningDto(
                error.instanceKey(),
                error.slotKey(),
                LENGTH_WARNING_CODE,
                LENGTH_WARNING_MESSAGE,
                slotOrder == null ? null : slotOrder.recommendedMaxLength(),
                content == null ? null : content.length()
        );
    }

    private Map<CopySlotKey, SlotOrder> buildTargetSlotMap(CopyGenerationContext context) {
        if (context == null || context.sortedBlocks().isEmpty() || context.targetSlots().isEmpty()) {
            return Map.of();
        }

        Map<CopySlotKey, SlotOrder> slotMap = new HashMap<>();

        for (BlockCopyContext block : context.sortedBlocks()) {
            for (TextSlotCopyContext slot : block.textSlots()) {
                CopySlotKey key = new CopySlotKey(block.instanceKey(), slot.slotKey());
                if (context.targetSlots().contains(key)) {
                    slotMap.put(key, new SlotOrder(
                            block.blockOrder(),
                            slot.slotOrder(),
                            slot.recommendedMaxLength()
                    ));
                }
            }
        }

        return slotMap;
    }

    private CopySlotKey toKey(CopyResultDto copy) {
        if (copy == null) {
            return new CopySlotKey(null, null);
        }

        return new CopySlotKey(copy.getInstanceKey(), copy.getSlotKey());
    }

    private List<CopyResultDto> safeCopies(CopyGenerationResult result) {
        if (result == null) {
            return List.of();
        }

        return result.copies();
    }

    private List<CopyValidationError> safeErrors(CopyValidationResult validationResult) {
        if (validationResult == null) {
            return List.of();
        }

        return validationResult.errors();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record SlotOrder(
            Integer blockOrder,
            Integer slotOrder,
            Integer recommendedMaxLength
    ) implements Comparable<SlotOrder> {

        @Override
        public int compareTo(SlotOrder other) {
            Comparator<Integer> comparator = Comparator.nullsLast(Integer::compareTo);
            int blockCompare = comparator.compare(blockOrder, other.blockOrder);
            if (blockCompare != 0) {
                return blockCompare;
            }

            return comparator.compare(slotOrder, other.slotOrder);
        }
    }
}
