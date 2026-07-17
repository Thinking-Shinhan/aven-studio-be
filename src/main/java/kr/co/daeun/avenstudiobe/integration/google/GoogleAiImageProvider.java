package kr.co.daeun.avenstudiobe.integration.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GoogleAiImageProvider {

    private static final String GENERATE_CONTENT_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private final GoogleAiProperties properties;
    @Qualifier("googleAiRestClient")
    private final RestClient restClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeneratedImage generate(String prompt) {
        return generate(prompt, Collections.emptyList());
    }

    public GeneratedImage generate(String prompt, List<ReferenceImage> referenceImages) {
        return generate(prompt, referenceImages, null);
    }

    public GeneratedImage generate(
            String prompt,
            List<ReferenceImage> referenceImages,
            String aspectRatio
    ) {
        return generate(prompt, referenceImages, aspectRatio, null);
    }

    public GeneratedImage generate(
            String prompt,
            List<ReferenceImage> referenceImages,
            String aspectRatio,
            String finalInstruction
    ) {
        validateApiKey();

        Map<String, Object> requestBody = buildRequestBody(
                prompt,
                referenceImages,
                aspectRatio,
                finalInstruction
        );

        String response;
        try {
            response = restClient.post()
                    .uri(GENERATE_CONTENT_URL.formatted(properties.getImageModel()))
                    .header("x-goog-api-key", properties.getApiKey())
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
        } catch (ResourceAccessException e) {
            throw new IllegalStateException(
                    "Google AI API 응답 대기 중 연결 오류가 발생했습니다. "
                            + "이미지 생성에 시간이 오래 걸리면 google.ai.timeout-seconds 설정을 늘려주세요.",
                    e
            );
        }

        return parseGeneratedImage(response);
    }

    Map<String, Object> buildRequestBody(
            String prompt,
            List<ReferenceImage> referenceImages,
            String aspectRatio
    ) {
        return buildRequestBody(prompt, referenceImages, aspectRatio, null);
    }

    Map<String, Object> buildRequestBody(
            String prompt,
            List<ReferenceImage> referenceImages,
            String aspectRatio,
            String finalInstruction
    ) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("contents", List.of(Map.of(
                "role", "user",
                "parts", buildParts(prompt, referenceImages, finalInstruction)
        )));

        if (aspectRatio != null && !aspectRatio.isBlank()) {
            Map<String, Object> generationConfig = new LinkedHashMap<>();
            generationConfig.put("imageConfig", Map.of(
                    "aspectRatio", aspectRatio
            ));
            requestBody.put("generationConfig", generationConfig);
        }

        return requestBody;
    }

    private List<Map<String, Object>> buildParts(
            String prompt,
            List<ReferenceImage> referenceImages,
            String finalInstruction
    ) {
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of(
                "text", prompt
        ));

        if (referenceImages != null) {
            for (ReferenceImage referenceImage : referenceImages) {
                parts.add(Map.of(
                        "text", buildReferenceRoleInstruction(referenceImage)
                ));
                parts.add(Map.of(
                        "inlineData", Map.of(
                                "mimeType", normalizeMimeType(referenceImage.contentType()),
                                "data", Base64.getEncoder().encodeToString(referenceImage.bytes())
                        )
                ));
            }
        }

        if (finalInstruction != null && !finalInstruction.isBlank()) {
            parts.add(Map.of(
                    "text", finalInstruction
            ));
        }

        return parts;
    }

    private String buildReferenceRoleInstruction(ReferenceImage referenceImage) {
        String header = "Reference image key: " + referenceImage.key()
                + "\nReference role: " + referenceImage.role();

        return switch (referenceImage.role()) {
            case "POSE_REFERENCE" -> header + "\n" + """
                    This image is POSE_REFERENCE.
                    Extract only the relative arrangement of the head, torso, arms, hands, legs, and feet.
                    Do not copy the camera position or visible front-facing viewpoint from this image.
                    The requested cameraAngle determines which side of this unchanged pose is photographed.
                    """.strip();
            case "MODEL_REFERENCE" -> header + "\n" + """
                    This image is MODEL_REFERENCE.
                    Use it only for identity, face, body proportions, skin tone, and hair.
                    Do not copy its camera viewpoint, body direction, clothing, or background.
                    """.strip();
            case "PRODUCT_FRONT" -> header + "\n"
                    + "Use this image as the garment front accuracy reference only.";
            case "PRODUCT_BACK" -> header + "\n"
                    + "Use this image as the garment rear accuracy reference, including rear silhouette, seams, and details.";
            case "GARMENT_REFERENCE" -> header + "\n"
                    + "Use this image only for garment identity, structure, color, material, and construction accuracy.";
            case "BACKGROUND_REFERENCE" -> header + "\n"
                    + "Use this image only for background space, lighting, depth, material mood, and atmosphere.";
            case "SHOT_COMPOSITION_REFERENCE" -> header + "\n"
                    + "Use this image only for shot composition and presentation. Never copy the product shown in it.";
            default -> header;
        };
    }

    public String getImageModel() {
        return properties.getImageModel();
    }

    String getGenerateContentUrl() {
        return GENERATE_CONTENT_URL.formatted(properties.getImageModel());
    }

    private GeneratedImage parseGeneratedImage(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            for (JsonNode candidate : root.path("candidates")) {
                for (JsonNode part : candidate.path("content").path("parts")) {
                    JsonNode inlineData = part.path("inlineData");
                    if (inlineData.isMissingNode()) {
                        inlineData = part.path("inline_data");
                    }

                    String data = inlineData.path("data").asText(null);
                    if (data != null && !data.isBlank()) {
                        String mimeType = inlineData.path("mimeType")
                                .asText(inlineData.path("mime_type").asText("image/png"));
                        return toGeneratedImage(
                                Base64.getDecoder().decode(data),
                                normalizeMimeType(mimeType)
                        );
                    }
                }
            }

            throw new IllegalStateException("Google AI 응답에서 생성 이미지를 찾을 수 없습니다.");
        } catch (Exception e) {
            throw new IllegalStateException("Google AI 이미지 응답 파싱에 실패했습니다.", e);
        }
    }

    private GeneratedImage toGeneratedImage(byte[] bytes, String contentType) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new IllegalStateException("생성 이미지의 크기를 읽을 수 없습니다.");
            }
            return new GeneratedImage(bytes, contentType, image.getWidth(), image.getHeight());
        } catch (Exception e) {
            throw new IllegalStateException("생성 이미지의 크기 분석에 실패했습니다.", e);
        }
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
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
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
            String contentType,
            int width,
            int height
    ) {
    }

    public record ReferenceImage(
            String key,
            String role,
            byte[] bytes,
            String contentType
    ) {
    }
}
