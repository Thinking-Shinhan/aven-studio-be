package kr.co.daeun.avenstudiobe.domain.detailpage.ai;

import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeClient;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeOperation;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AutoPlacementAiClient {

    private final ClaudeClient claudeClient;
    private final ClaudeProperties claudeProperties;
    private final AutoPlacementAiResponseParser parser;

    public AutoPlacementAiResponseDto requestPlacement(String systemPrompt, String userPrompt) {
        String rawText = claudeClient.requestText(
                ClaudeOperation.AUTO_PLACEMENT,
                systemPrompt,
                userPrompt,
                claudeProperties.getImagePlacementTimeoutSeconds()
        );
        return parser.parse(rawText);
    }
}
