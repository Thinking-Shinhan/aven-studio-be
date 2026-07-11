package kr.co.daeun.avenstudiobe.domain.imagegeneration.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageGenerationServiceTest {

    private final ImageGenerationService service = new ImageGenerationService(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
    );
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void omittedUserOptionsDoNotThrowWhenBuildingMetadata() throws Exception {
        JsonNode emptyJson = objectMapper.createObjectNode();

        assertDoesNotThrow(() -> invokeMetadataBuilder("buildModelCutMetadata", emptyJson));
        assertDoesNotThrow(() -> invokeMetadataBuilder("buildProductCutMetadata", emptyJson));
    }

    @Test
    void productCutInstructionTreatsShotReferenceAsCompositionOnly() throws Exception {
        String instruction = invokeSystemInstruction("PRODUCT_CUT");

        assertTrue(instruction.contains("shotReferenceImage = SHOT_COMPOSITION_REFERENCE"));
        assertTrue(instruction.contains("Never use the product shown inside SHOT_COMPOSITION_REFERENCE"));
        assertTrue(instruction.contains("The final product must be the exact product from GARMENT_REFERENCE"));
        assertFalse(instruction.contains("Only one model may appear"));
    }

    @Test
    void modelCutInstructionKeepsModelReferenceRules() throws Exception {
        String instruction = invokeSystemInstruction("MODEL_CUT");

        assertTrue(instruction.contains("modelImage = MODEL_REFERENCE"));
        assertTrue(instruction.contains("Only one model may appear"));
    }

    private void invokeMetadataBuilder(String methodName, JsonNode emptyJson) throws Throwable {
        Method method = ImageGenerationService.class.getDeclaredMethod(methodName, JsonNode.class, JsonNode.class);
        method.setAccessible(true);

        try {
            method.invoke(service, emptyJson, emptyJson);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private String invokeSystemInstruction(String cutType) throws Exception {
        Method method = ImageGenerationService.class.getDeclaredMethod("buildSystemInstruction", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, cutType);
    }
}
