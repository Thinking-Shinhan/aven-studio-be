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
        List<ImageGenerationResultRespDto> results = List.of(
                result(0),
                result(1)
        );

        ImageGenerationJobRespDto response = ImageGenerationJobRespDto.from(job, results);

        assertEquals(50, response.getProgressPercent());
        assertEquals("GENERATING", response.getProgressStage());
        assertEquals(4, response.getProgressItems().size());
        assertEquals("COMPLETED", response.getProgressItems().get(0).status());
        assertEquals("이미지 1장 생성 완료", response.getProgressItems().get(0).message());
        assertEquals("PENDING", response.getProgressItems().get(2).status());
    }

    @Test
    void returnsOneHundredPercentWhenSucceeded() throws Exception {
        ImageGenerationJob job = job("SUCCEEDED", 4, 4);

        ImageGenerationJobRespDto response = ImageGenerationJobRespDto.from(job, List.of());

        assertEquals(100, response.getProgressPercent());
        assertEquals("COMPLETED", response.getProgressStage());
        assertEquals("COMPLETED", response.getProgressItems().get(3).status());
    }

    @Test
    void returnsFifteenPercentBeforeFirstImageCompletes() throws Exception {
        ImageGenerationJob job = job("PROCESSING", 4, 0);

        ImageGenerationJobRespDto response = ImageGenerationJobRespDto.from(job, List.of());

        assertEquals(15, response.getProgressPercent());
        assertEquals(4, response.getProgressItems().size());
        assertEquals("PENDING", response.getProgressItems().get(0).status());
    }

    @Test
    void marksIncompleteItemsAsFailedWhenJobFails() throws Exception {
        ImageGenerationJob job = job("FAILED", 4, 1);

        ImageGenerationJobRespDto response = ImageGenerationJobRespDto.from(job, List.of(result(0)));

        assertEquals(25, response.getProgressPercent());
        assertEquals("COMPLETED", response.getProgressItems().get(0).status());
        assertEquals("FAILED", response.getProgressItems().get(1).status());
    }

    private ImageGenerationResultRespDto result(int outputIndex) {
        return new ImageGenerationResultRespDto(
                (long) outputIndex + 1,
                (long) outputIndex + 1,
                outputIndex,
                "GENERATED",
                "https://example.com/image.png",
                null
        );
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
