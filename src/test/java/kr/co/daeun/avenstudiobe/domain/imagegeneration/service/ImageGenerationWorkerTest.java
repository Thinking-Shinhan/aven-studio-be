package kr.co.daeun.avenstudiobe.domain.imagegeneration.service;

import kr.co.daeun.avenstudiobe.domain.asset.entity.Asset;
import kr.co.daeun.avenstudiobe.domain.asset.service.AssetService;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.repository.ProductRepository;
import kr.co.daeun.avenstudiobe.global.exception.ImageGenerationQueueFullException;
import kr.co.daeun.avenstudiobe.integration.google.GoogleAiImageProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageGenerationWorkerTest {

    private final GoogleAiImageProvider provider = mock(GoogleAiImageProvider.class);
    private final AspectRatioImageCropper imageCropper = new AspectRatioImageCropper();
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final AssetService assetService = mock(AssetService.class);
    private final ImageGenerationProgressService progressService = mock(ImageGenerationProgressService.class);
    private final TaskExecutor directExecutor = Runnable::run;

    @Test
    void savesEachOutputAndMarksJobSucceeded() {
        Product product = mock(Product.class);
        Asset asset = mock(Asset.class);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(provider.generate(anyString(), any(), anyString(), anyString())).thenReturn(generatedImage());
        when(assetService.saveGeneratedImage(any(), anyString(), any(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(asset);
        ImageGenerationWorker worker = worker(directExecutor);

        worker.submit(work("first", "second", "third", "fourth"));

        verify(progressService).markProcessing(10L);
        verify(provider, times(4)).generate(anyString(), any(), eq("3:4"), anyString());
        verify(progressService, times(4)).recordGeneratedResult(anyLong(), any(), anyInt());
        verify(progressService).markSucceeded(anyLong(), anyInt());
        verify(progressService, never()).markFailed(anyLong(), anyString(), anyInt());
    }

    @Test
    void keepsSuccessfulOutputsAndMarksJobFailedWhenOneTaskFails() {
        Product product = mock(Product.class);
        Asset asset = mock(Asset.class);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(provider.generate(anyString(), any(), anyString(), anyString())).thenAnswer(invocation -> {
            if ("second".equals(invocation.getArgument(0))) {
                throw new IllegalStateException("provider failed");
            }
            return generatedImage();
        });
        when(assetService.saveGeneratedImage(any(), anyString(), any(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(asset);
        ImageGenerationWorker worker = worker(directExecutor);

        worker.submit(work("first", "second"));

        verify(progressService).recordGeneratedResult(anyLong(), any(), anyInt());
        verify(progressService).markFailed(anyLong(), anyString(), anyInt());
        verify(progressService, never()).markSucceeded(anyLong(), anyInt());
    }

    @Test
    void storesSafeFailureCodeForInvalidGoogleRequest() {
        when(provider.generate(anyString(), any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException(
                        "Google AI API 요청에 실패했습니다. status=400, response=INVALID_ARGUMENT"
                ));
        ImageGenerationWorker worker = worker(directExecutor);

        worker.submit(work("first"));

        verify(progressService).markFailed(
                eq(10L),
                eq("[GOOGLE_API_INVALID_REQUEST] Google AI 이미지 생성 요청 형식이 올바르지 않습니다."),
                anyInt()
        );
    }

    @Test
    void retriesTemporaryGoogleFailureAndSavesResult() {
        Product product = mock(Product.class);
        Asset asset = mock(Asset.class);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(provider.generate(anyString(), any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException(
                        "Google AI API 요청에 실패했습니다. status=503, response=UNAVAILABLE"
                ))
                .thenReturn(generatedImage());
        when(assetService.saveGeneratedImage(any(), anyString(), any(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(asset);
        ImageGenerationWorker worker = worker(directExecutor);

        worker.submit(work("first"));

        verify(provider, times(2)).generate(anyString(), any(), eq("3:4"), anyString());
        verify(progressService).recordGeneratedResult(10L, asset, 0);
        verify(progressService).markSucceeded(anyLong(), anyInt());
        verify(progressService, never()).markFailed(anyLong(), anyString(), anyInt());
    }

    @Test
    void storesSafeFailureCodeForRepeatedTemporaryGoogleFailure() {
        when(provider.generate(anyString(), any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException(
                        "Google AI API 요청에 실패했습니다. status=503, response=UNAVAILABLE"
                ));
        ImageGenerationWorker worker = worker(directExecutor);

        worker.submit(work("first"));

        verify(provider, times(2)).generate(anyString(), any(), eq("3:4"), anyString());
        verify(progressService).markFailed(
                eq(10L),
                eq("[GOOGLE_API_TEMPORARY_UNAVAILABLE] Google AI 이미지 생성 서버가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요."),
                anyInt()
        );
    }

    @Test
    void returnsQueueFullWhenJobExecutorRejectsTask() {
        TaskExecutor rejectingExecutor = task -> {
            throw new TaskRejectedException("queue full");
        };
        ImageGenerationWorker worker = worker(rejectingExecutor);

        assertThrows(ImageGenerationQueueFullException.class, () -> worker.submit(work("first")));
        verify(progressService).markFailed(anyLong(), anyString(), anyInt());
    }

    @Test
    void cropsMismatchedAspectRatioAndCallsProviderOnce() {
        Product product = mock(Product.class);
        Asset asset = mock(Asset.class);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(provider.generate(anyString(), any(), anyString(), anyString()))
                .thenReturn(generatedImage(1344, 768));
        when(assetService.saveGeneratedImage(any(), anyString(), any(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(asset);
        ImageGenerationWorker worker = worker(directExecutor);

        worker.submit(work("first"));

        verify(provider, times(1)).generate(anyString(), any(), eq("3:4"), anyString());
        ArgumentCaptor<byte[]> imageBytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(assetService, times(1)).saveGeneratedImage(
                imageBytesCaptor.capture(),
                anyString(),
                any(),
                anyLong(),
                anyString(),
                anyString(),
                anyString()
        );
        assertImageDimensions(imageBytesCaptor.getValue(), 576, 768);
        verify(progressService).recordGeneratedResult(10L, asset, 0);
        verify(progressService).markSucceeded(anyLong(), anyInt());
    }

    @Test
    void failsJobBeforeProviderCallWhenAspectRatioIsMissing() {
        ImageGenerationWorker worker = worker(directExecutor);
        ImageGenerationWork.Task task = new ImageGenerationWork.Task(
                0,
                "prompt",
                List.of(),
                null,
                "BACK_VIEW",
                "FULL_BODY",
                "final instruction",
                "FULL_BODY",
                "description"
        );

        worker.submit(new ImageGenerationWork(
                10L,
                1L,
                System.currentTimeMillis(),
                List.of(task)
        ));

        verify(provider, never()).generate(anyString(), any(), any(), any());
        verify(assetService, never()).saveGeneratedImage(
                any(), anyString(), any(), anyLong(), anyString(), anyString(), anyString()
        );
        verify(progressService).markFailed(anyLong(), anyString(), anyInt());
    }

    private ImageGenerationWorker worker(TaskExecutor jobExecutor) {
        return new ImageGenerationWorker(
                provider,
                imageCropper,
                productRepository,
                assetService,
                progressService,
                jobExecutor,
                directExecutor
        );
    }

    private ImageGenerationWork work(String... prompts) {
        List<ImageGenerationWork.Task> tasks = java.util.stream.IntStream.range(0, prompts.length)
                .mapToObj(index -> new ImageGenerationWork.Task(
                        index,
                        prompts[index],
                        List.of(),
                        "3:4",
                        "BACK_VIEW",
                        "FULL_BODY",
                        "final instruction",
                        "FULL_BODY",
                        "description"
                ))
                .toList();
        return new ImageGenerationWork(10L, 1L, System.currentTimeMillis(), tasks);
    }

    private GoogleAiImageProvider.GeneratedImage generatedImage() {
        return generatedImage(900, 1200);
    }

    private GoogleAiImageProvider.GeneratedImage generatedImage(int width, int height) {
        try {
            java.awt.image.BufferedImage source =
                    new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(source, "png", output);
            return new GoogleAiImageProvider.GeneratedImage(
                    output.toByteArray(),
                    "image/png",
                    width,
                    height
            );
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertImageDimensions(byte[] bytes, int expectedWidth, int expectedHeight) {
        try {
            java.awt.image.BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            assertEquals(expectedWidth, image.getWidth());
            assertEquals(expectedHeight, image.getHeight());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
