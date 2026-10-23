package kr.co.daeun.avenstudiobe.global.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.daeun.avenstudiobe.domain.detailpage.ai.ClaudeCopyReviewer;
import kr.co.daeun.avenstudiobe.domain.detailpage.ai.CopyReviewPromptBuilder;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeClient;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class LegacyJacksonConfigTest {

    @Test
    void providesJackson2MapperAndSatisfiesClaudeCopyReviewerConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(LegacyJacksonConfig.class, ClaudeCopyReviewer.class);
            context.registerBean(CopyReviewPromptBuilder.class, () -> mock(CopyReviewPromptBuilder.class));
            context.registerBean(ClaudeClient.class, () -> mock(ClaudeClient.class));
            context.registerBean(ClaudeProperties.class, ClaudeProperties::new);
            context.refresh();

            assertNotNull(context.getBean(ObjectMapper.class));
            assertNotNull(context.getBean(ClaudeCopyReviewer.class));
        }
    }
}
