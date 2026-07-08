package kr.co.daeun.avenstudiobe.integration.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class GoogleAiImageProvider {

    private static final String INTERACTIONS_URL = "https://generativelanguage.googleapis.com/v1beta/interactions";

    @Value("${google.ai.api-key:}")
    private String apiKey;

    @Value("${google.ai.image-model:gemini-2.5-flash-image}")
    private String imageModel;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    public GeneratedImage generate(String prompt) {
        return generate(prompt, Collections.emptyList());
    }

    public GeneratedImage generate(String prompt, List<ReferenceImage> referenceImages) {
        validateApiKey();

        Map<String, Object> requestBody = Map.of(
                "model", imageModel,
                "input", buildInput(prompt, referenceImages)
        );

        String response;
        try {
            response = restClient.post()
                    .uri(INTERACTIONS_URL)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException(
                    "Google AI API 요청에 실패했습니다. status=" + e.getStatusCode().value()
                            + ", response=" + abbreviate(e.getResponseBodyAsString()),
                    e
            );
        }

        return parseGeneratedImage(response);
    }

    private List<Map<String, Object>> buildInput(String prompt, List<ReferenceImage> referenceImages) {
        List<Map<String, Object>> input = new ArrayList<>();
        input.add(Map.of(
                "type", "text",
                "text", prompt
        ));

        if (referenceImages == null || referenceImages.isEmpty()) {
            return input;
        }

        for (ReferenceImage referenceImage : referenceImages) {
            input.add(Map.of(
                    "type", "text",
                    "text", "Reference image key: " + referenceImage.key()
            ));
            input.add(Map.of(
                    "type", "image",
                    "mime_type", normalizeMimeType(referenceImage.contentType()),
                    "data", Base64.getEncoder().encodeToString(referenceImage.bytes())
            ));
        }

        return input;
    }

    public String getImageModel() {
        return imageModel;
    }

    private GeneratedImage parseGeneratedImage(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            JsonNode outputImage = root.path("output_image");
            if (!outputImage.isMissingNode()) {
                String data = outputImage.path("data").asText(null);
                String mimeType = outputImage.path("mime_type").asText("image/png");

                if (data != null && !data.isBlank()) {
                    return new GeneratedImage(Base64.getDecoder().decode(data), normalizeMimeType(mimeType));
                }
            }

            GeneratedImage found = findImageData(root);
            if (found != null) {
                return found;
            }

            throw new IllegalStateException("Google AI 응답에서 생성 이미지를 찾을 수 없습니다.");
        } catch (Exception e) {
            throw new IllegalStateException("Google AI 이미지 응답 파싱에 실패했습니다.", e);
        }
    }

    private GeneratedImage findImageData(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        if (node.isObject()) {
            String data = node.path("data").asText(null);
            String mimeType = node.path("mime_type").asText(node.path("mimeType").asText("image/png"));
            String type = node.path("type").asText("");

            if (data != null && !data.isBlank() && (type.equals("image") || mimeType.startsWith("image/"))) {
                return new GeneratedImage(Base64.getDecoder().decode(data), normalizeMimeType(mimeType));
            }
        }

        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                GeneratedImage found = findImageData(child);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private String normalizeMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return "image/png";
        }

        if (mimeType.equals("image/jpg")) {
            return "image/jpeg";
        }

        return mimeType;
    }

    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GOOGLE_AI_API_KEY 환경변수가 설정되어 있지 않습니다.");
        }
    }

    private String abbreviate(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        int maxLength = 1000;
        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength) + "...";
    }

    public record GeneratedImage(
            byte[] bytes,
            String contentType
    ) {
    }

    public record ReferenceImage(
            String key,
            byte[] bytes,
            String contentType
    ) {
    }
}
