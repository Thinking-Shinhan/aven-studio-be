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

    @Test
    void placesReferenceRoleInstructionsNextToImagesAndFinalConstraintLast() {
        List<GoogleAiImageProvider.ReferenceImage> references = List.of(
                reference("poseImage", "POSE_REFERENCE"),
                reference("modelImage", "MODEL_REFERENCE"),
                reference("productImages[0]", "PRODUCT_FRONT"),
                reference("productImages[1]", "PRODUCT_BACK")
        );

        Map<String, Object> requestBody = provider.buildRequestBody(
                "compiled prompt",
                references,
                "9:16",
                "[FINAL NON-NEGOTIABLE OUTPUT CONSTRAINTS]\nexact 9:16"
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> input = (List<Map<String, Object>>) requestBody.get("input");

        assertTrue(input.get(1).get("text").toString().contains("This image is POSE_REFERENCE"));
        assertEquals("image", input.get(2).get("type"));
        assertTrue(input.get(3).get("text").toString().contains("This image is MODEL_REFERENCE"));
        assertTrue(input.get(5).get("text").toString().contains("garment front accuracy"));
        assertTrue(input.get(7).get("text").toString().contains("garment rear accuracy"));
        assertTrue(input.get(input.size() - 1).get("text").toString().contains("FINAL NON-NEGOTIABLE"));
    }

    @Test
    void parsesGeneratedImageDimensionsForAspectRatioValidation() throws Exception {
        BufferedImage source = new BufferedImage(768, 1344, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(source, "png", output);
        String response = """
                {
                  "output_image": {
                    "mime_type": "image/png",
                    "data": "%s"
                  }
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
        return new GoogleAiImageProvider.ReferenceImage(key, role, new byte[]{1}, "image/png");
    }

    private GoogleAiProperties properties() {
        GoogleAiProperties properties = new GoogleAiProperties();
        properties.setImageModel("gemini-test-image");
        return properties;
    }
}
