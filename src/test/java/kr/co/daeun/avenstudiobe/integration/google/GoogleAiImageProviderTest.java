package kr.co.daeun.avenstudiobe.integration.google;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class GoogleAiImageProviderTest {

    private final GoogleAiProperties properties = properties();
    private final GoogleAiImageProvider provider = new GoogleAiImageProvider(
            properties,
            mock(RestClient.class)
    );

    @Test
    void includesAspectRatioInStructuredResponseFormat() {
        Map<String, Object> requestBody = provider.buildRequestBody(
                "prompt",
                List.of(),
                "3:4"
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> responseFormat = (Map<String, Object>) requestBody.get("response_format");

        assertEquals("image", responseFormat.get("type"));
        assertEquals("image/png", responseFormat.get("mime_type"));
        assertEquals("3:4", responseFormat.get("aspect_ratio"));
    }

    @Test
    void omitsResponseFormatWhenAspectRatioIsNotSelected() {
        Map<String, Object> requestBody = provider.buildRequestBody(
                "prompt",
                List.of(),
                null
        );

        assertFalse(requestBody.containsKey("response_format"));
    }

    private GoogleAiProperties properties() {
        GoogleAiProperties properties = new GoogleAiProperties();
        properties.setImageModel("gemini-test-image");
        return properties;
    }
}
