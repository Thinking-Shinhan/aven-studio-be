package kr.co.daeun.avenstudiobe.domain.detailpage.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.BlockCopyContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyGenerationContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyResultDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopySlotKey;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.TextSlotCopyContext;
import org.junit.jupiter.api.Test;

class GeneratedCopyValidatorTest {

    private final GeneratedCopyValidator validator = new GeneratedCopyValidator();

    @Test
    void replacesLineBreaksInContentWithSpaces() {
        CopyGenerationContext context = new CopyGenerationContext(
                null,
                List.of(new BlockCopyContext(
                        1,
                        "block-1",
                        "template",
                        List.of(),
                        List.of(),
                        List.of(new TextSlotCopyContext("headline", 1, "HEADLINE", 100, null, true))
                )),
                Set.of(new CopySlotKey("block-1", "headline"))
        );
        CopyResultDto copy = new CopyResultDto("block-1", "headline", "first\r\nsecond\nthird");

        List<CopyResultDto> result = validator.sortCopies(context, List.of(copy));

        assertEquals("first second third", result.get(0).getContent());
    }
}
