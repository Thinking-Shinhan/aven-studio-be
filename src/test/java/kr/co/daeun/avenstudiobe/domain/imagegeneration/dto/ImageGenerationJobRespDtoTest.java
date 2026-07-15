package kr.co.daeun.avenstudiobe.domain.imagegeneration.dto;

import kr.co.daeun.avenstudiobe.domain.imagegeneration.entity.ImageGenerationJob;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageGenerationJobRespDtoTest {

    @Test
    void calculatesProgressFromStatusAndCompletedOutputCount() throws Exception {
        ImageGenerationJob job = job("PROCESSING", 4, 2);

        ImageGenerationJobRespDto response = ImageGenerationJobRespDto.from(job, List.of());

        assertEquals(55, response.getProgressPercent());
        assertEquals("GENERATING", response.getProgressStage());
        assertEquals("4장 중 2장이 생성되었습니다.", response.getProgressMessage());
    }

    @Test
    void returnsOneHundredPercentWhenSucceeded() throws Exception {
        ImageGenerationJob job = job("SUCCEEDED", 4, 4);

        ImageGenerationJobRespDto response = ImageGenerationJobRespDto.from(job, List.of());

        assertEquals(100, response.getProgressPercent());
        assertEquals("COMPLETED", response.getProgressStage());
    }

    private ImageGenerationJob job(String status, int requestedCount, int outputCount) throws Exception {
        ImageGenerationJob job = ImageGenerationJob.create(
                null,
                null,
                null,
                null,
                "MODEL_CUT",
                "PARALLEL",
                "prompt",
                null,
                "{}",
                "{}",
                "gemini-test",
                requestedCount
        );
        set(job, "status", status);
        set(job, "requestedCount", requestedCount);
        set(job, "outputCount", outputCount);
        return job;
    }

    private void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
