package kr.co.daeun.avenstudiobe.integration.google;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class GoogleAiImageProviderTest {

    private final GoogleAiProperties properties = properties();
    private final GoogleAiImageProvider provider = new GoogleAiImageProvider(
            properties,
            mock(RestClient.class)
    );

    @Test
    void usesV1BetaGenerateContentEndpointForImageConfig() {
        assertEquals(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-test-image:generateContent",
                provider.getGenerateContentUrl()
        );
    }

    @Test
    void includesAspectRatioInImageConfig() {
        Map<String, Object> requestBody = provider.buildRequestBody(
                "prompt",
                List.of(),
                "3:4"
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> generationConfig =
                (Map<String, Object>) requestBody.get("generationConfig");
        @SuppressWarnings("unchecked")
        Map<String, Object> imageConfig =
                (Map<String, Object>) generationConfig.get("imageConfig");

        assertFalse(generationConfig.containsKey("responseModalities"));
        assertFalse(generationConfig.containsKey("responseFormat"));
        assertEquals("3:4", imageConfig.get("aspectRatio"));
    }

    @Test
    void omitsGenerationConfigWhenAspectRatioIsNotSelected() {
        Map<String, Object> requestBody = provider.buildRequestBody(
                "prompt",
                List.of(),
                null
        );

        assertFalse(requestBody.containsKey("generationConfig"));
    }

    @Test
    void placesReferenceRoleInstructionsNextToImagesAndFinalConstraintLast() {
        List<GoogleAiImageProvider.ReferenceImage> references = List.of(
                reference("poseImage", "POSE_REFERENCE"),
                reference("modelImage", "MODEL_REFERENCE"),
                new GoogleAiImageProvider.ReferenceImage(
                        "productImages[0]",
                        "PRODUCT_FRONT",
                        "웜톤으로 보정된 생성 이미지",
                        new byte[]{1},
                        "image/png"
                ),
                reference("productImages[1]", "PRODUCT_BACK"),
                new GoogleAiImageProvider.ReferenceImage(
                        "backgroundImage",
                        "BACKGROUND_REFERENCE",
                        "따뜻한 베이지 톤의 무광 스튜디오 배경과 부드러운 접지 그림자.",
                        new byte[]{1},
                        "image/png"
                )
        );

        Map<String, Object> requestBody = provider.buildRequestBody(
                "compiled prompt",
                references,
                "9:16",
                "[FINAL NON-NEGOTIABLE OUTPUT CONSTRAINTS]\nexact 9:16"
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contents =
                (List<Map<String, Object>>) requestBody.get("contents");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts =
                (List<Map<String, Object>>) contents.get(0).get("parts");

        assertTrue(parts.get(1).get("text").toString().contains("This image is POSE_REFERENCE"));
        assertTrue(parts.get(2).containsKey("inlineData"));
        assertTrue(parts.get(3).get("text").toString().contains("This image is MODEL_REFERENCE"));
        assertTrue(parts.get(3).get("text").toString().contains("non-negotiable identity reference"));
        assertTrue(parts.get(3).get("text").toString().contains("same exact person"));
        assertTrue(parts.get(5).get("text").toString().contains("garment front accuracy"));
        assertFalse(parts.get(5).get("text").toString().contains("웜톤으로 보정된 생성 이미지"));
        assertTrue(parts.get(7).get("text").toString().contains("garment rear accuracy"));
        assertTrue(parts.get(9).get("text").toString().contains("Background scene description"));
        assertTrue(parts.get(9).get("text").toString().contains("background scene recipe"));
        assertTrue(parts.get(9).get("text").toString().contains("original white balance and actual color"));
        assertFalse(parts.get(9).get("text").toString().contains("background color temperature"));
        assertTrue(parts.get(9).get("text").toString().contains("Never alter the target product's actual color"));
        assertTrue(parts.get(parts.size() - 1).get("text").toString().contains("FINAL NON-NEGOTIABLE"));
    }

    @Test
    void placesProductReferencesLastAndOmitsShotDescriptionForProductCut() {
        List<GoogleAiImageProvider.ReferenceImage> references = List.of(
                reference("productImages[0]", "PRODUCT_FRONT"),
                reference("backgroundImage", "BACKGROUND_REFERENCE"),
                new GoogleAiImageProvider.ReferenceImage(
                        "shotReferenceImage",
                        "SHOT_COMPOSITION_REFERENCE",
                        "다른 상품의 색상과 로고가 포함된 긴 샷 설명",
                        new byte[]{1},
                        "image/png"
                ),
                reference("productImages[1]", "PRODUCT_BACK")
        );

        Map<String, Object> requestBody = provider.buildRequestBody(
                "compiled prompt",
                references,
                "3:4",
                "[FINAL NON-NEGOTIABLE OUTPUT CONSTRAINTS]"
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contents =
                (List<Map<String, Object>>) requestBody.get("contents");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts =
                (List<Map<String, Object>>) contents.get(0).get("parts");

        String backgroundInstruction = parts.get(1).get("text").toString();
        String shotInstruction = parts.get(3).get("text").toString();
        String productFrontInstruction = parts.get(5).get("text").toString();
        String productBackInstruction = parts.get(7).get("text").toString();

        assertTrue(backgroundInstruction.contains("BACKGROUND_REFERENCE"));
        assertTrue(shotInstruction.contains("SHOT_COMPOSITION_REFERENCE"));
        assertFalse(shotInstruction.contains("다른 상품의 색상과 로고"));
        assertFalse(shotInstruction.contains("Composition-only description"));
        assertTrue(productFrontInstruction.contains("PRODUCT_FRONT"));
        assertTrue(productBackInstruction.contains("PRODUCT_BACK"));
        assertTrue(parts.get(parts.size() - 1).get("text").toString().contains("FINAL NON-NEGOTIABLE"));
    }

    @Test
    void parsesGeneratedImageDimensionsForAspectRatioValidation() throws Exception {
        BufferedImage source = new BufferedImage(768, 1344, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(source, "png", output);
        String response = """
                {
                  "candidates": [{
                    "content": {
                      "parts": [{
                        "inlineData": {
                          "mimeType": "image/png",
                          "data": "%s"
                        }
                      }]
                    }
                  }]
                }
                """.formatted(Base64.getEncoder().encodeToString(output.toByteArray()));

        Method method = GoogleAiImageProvider.class.getDeclaredMethod("parseGeneratedImage", String.class);
        method.setAccessible(true);
        GoogleAiImageProvider.GeneratedImage image =
                (GoogleAiImageProvider.GeneratedImage) method.invoke(provider, response);

        assertEquals(768, image.width());
        assertEquals(1344, image.height());
        assertEquals("image/png", image.contentType());
    }

    private GoogleAiImageProvider.ReferenceImage reference(String key, String role) {
        return new GoogleAiImageProvider.ReferenceImage(key, role, null, new byte[]{1}, "image/png");
    }

    private GoogleAiProperties properties() {
        GoogleAiProperties properties = new GoogleAiProperties();
        properties.setImageModel("gemini-test-image");
        return properties;
    }
}
