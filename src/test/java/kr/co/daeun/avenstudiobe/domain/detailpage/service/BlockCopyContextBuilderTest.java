package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopySlotKey;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyTemplateBlockReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.TextSlotReqDto;
import org.junit.jupiter.api.Test;

class BlockCopyContextBuilderTest {

    private final BlockCopyContextBuilder builder = new BlockCopyContextBuilder();

    @Test
    void templateScopeKeepsBlankNonTargetTextAsContextOnly() {
        CopyTemplateBlockReqDto target = block(1, "target", textSlot("headline"));
        CopyTemplateBlockReqDto nonTarget = block(2, "non-target", textSlot("body"));

        BlockCopyContextBuilder.BuildResult result = builder.build(
                List.of(target, nonTarget),
                Map.of(),
                "target"
        );

        assertTrue(result.targetSlots().contains(new CopySlotKey("target", "headline")));
        assertFalse(result.targetSlots().contains(new CopySlotKey("non-target", "body")));
        assertFalse(result.sortedBlocks().get(1).textSlots().get(0).generationTarget());
    }

    @Test
    void nullScopeKeepsStandaloneBlankTextBehavior() {
        CopyTemplateBlockReqDto first = block(1, "first", textSlot("headline"));
        CopyTemplateBlockReqDto second = block(2, "second", textSlot("body"));

        BlockCopyContextBuilder.BuildResult result = builder.build(List.of(first, second), Map.of());

        assertTrue(result.targetSlots().contains(new CopySlotKey("first", "headline")));
        assertTrue(result.targetSlots().contains(new CopySlotKey("second", "body")));
    }

    private CopyTemplateBlockReqDto block(int blockOrder, String instanceKey, TextSlotReqDto textSlot) {
        CopyTemplateBlockReqDto block = new CopyTemplateBlockReqDto();
        block.setBlockOrder(blockOrder);
        block.setInstanceKey(instanceKey);
        block.setTemplateKey("template-" + instanceKey);
        block.setTemplateType("TEXT");
        block.setTextSlots(List.of(textSlot));
        return block;
    }

    private TextSlotReqDto textSlot(String slotKey) {
        TextSlotReqDto slot = new TextSlotReqDto();
        slot.setSlotKey(slotKey);
        slot.setSlotOrder(1);
        slot.setTextRole("HEADLINE");
        slot.setRecommendedMaxLength(30);
        slot.setCurrentContent(null);
        return slot;
    }
}
