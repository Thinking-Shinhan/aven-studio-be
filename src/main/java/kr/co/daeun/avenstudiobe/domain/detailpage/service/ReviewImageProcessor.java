package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.MeterRegistry;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ReviewAssetSource;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ReviewBlockContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ReviewImage;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeProperties;
import kr.co.daeun.avenstudiobe.global.storage.GcsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class ReviewImageProcessor {

    private static final String JPEG_MEDIA_TYPE = "image/jpeg";
    private static final float JPEG_QUALITY = 0.85f;

    private final GcsService gcsService;
    private final ClaudeProperties claudeProperties;
    private final MeterRegistry meterRegistry;
    private final Executor reviewImageExecutor;
    private final Cache<String, byte[]> reviewImageCache;

    public ReviewImageProcessor(
            GcsService gcsService,
            ClaudeProperties claudeProperties,
            MeterRegistry meterRegistry,
            @Qualifier("reviewImageExecutor") Executor reviewImageExecutor,
            @Qualifier("reviewImageCache") Cache<String, byte[]> reviewImageCache
    ) {
        this.gcsService = gcsService;
        this.claudeProperties = claudeProperties;
        this.meterRegistry = meterRegistry;
        this.reviewImageExecutor = reviewImageExecutor;
        this.reviewImageCache = reviewImageCache;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ProcessResult process(
            List<ReviewBlockContext> blocks,
            List<ReviewAssetSource> assetSources
    ) {
        List<ReviewBlockContext> safeBlocks = safeList(blocks);
        List<ImageCandidate> selectedCandidates = selectCandidates(safeBlocks);
        Set<Long> selectedAssetIds = selectedCandidates.stream()
                .map(ImageCandidate::assetId)
                .collect(Collectors.toSet());
        List<ReviewBlockContext> updatedBlocks = updateBlocks(safeBlocks, selectedAssetIds);

        Map<Long, ReviewAssetSource> sourceByAssetId = safeList(assetSources).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        ReviewAssetSource::assetId,
                        Function.identity(),
                        (first, ignored) -> first
                ));

        List<CompletableFuture<ReviewImage>> imageFutures = selectedCandidates.stream()
                .map(candidate -> CompletableFuture.supplyAsync(
                        () -> meterRegistry.timer("review.image.single").record(() ->
                                processImage(candidate.assetId(), sourceByAssetId.get(candidate.assetId()))
                        ),
                        reviewImageExecutor
                ))
                .toList();

        List<ReviewImage> images = imageFutures.stream()
                .map(this::joinImage)
                .toList();

        return new ProcessResult(updatedBlocks, images);
    }

    private ReviewImage joinImage(CompletableFuture<ReviewImage> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    private List<ImageCandidate> selectCandidates(List<ReviewBlockContext> blocks) {
        Map<Long, ImageCandidate> bestCandidateByAssetId = new LinkedHashMap<>();
        long sequence = 0;

        for (ReviewBlockContext block : blocks) {
            if (block == null || safeList(block.textSlots()).isEmpty()) {
                continue;
            }

            for (Long assetId : safeList(block.placedImageAssetIds())) {
                sequence = addCandidate(
                        bestCandidateByAssetId,
                        new ImageCandidate(assetId, ImagePriority.DIRECT, block.blockOrder(), sequence)
                );
            }
            for (Long assetId : safeList(block.associatedImageAssetIds())) {
                sequence = addCandidate(
                        bestCandidateByAssetId,
                        new ImageCandidate(assetId, ImagePriority.DIRECT, block.blockOrder(), sequence)
                );
            }
            for (Long assetId : safeList(block.neighboringImageAssetIds())) {
                sequence = addCandidate(
                        bestCandidateByAssetId,
                        new ImageCandidate(assetId, ImagePriority.NEIGHBORING, block.blockOrder(), sequence)
                );
            }
        }

        int maxImages = positiveOrDefault(claudeProperties.getReviewMaxImages(), 12);
        return bestCandidateByAssetId.values().stream()
                .sorted(candidateComparator())
                .limit(maxImages)
                .toList();
    }

    private long addCandidate(
            Map<Long, ImageCandidate> candidates,
            ImageCandidate candidate
    ) {
        if (candidate.assetId() != null) {
            candidates.merge(
                    candidate.assetId(),
                    candidate,
                    (existing, replacement) -> candidateComparator().compare(existing, replacement) <= 0
                            ? existing
                            : replacement
            );
        }
        return candidate.sequence() + 1;
    }

    private Comparator<ImageCandidate> candidateComparator() {
        return Comparator
                .comparing(ImageCandidate::priority)
                .thenComparing(
                        ImageCandidate::blockOrder,
                        Comparator.nullsLast(Integer::compareTo)
                )
                .thenComparingLong(ImageCandidate::sequence);
    }

    private List<ReviewBlockContext> updateBlocks(
            List<ReviewBlockContext> blocks,
            Set<Long> selectedAssetIds
    ) {
        return blocks.stream()
                .filter(Objects::nonNull)
                .map(block -> {
                    List<Long> originalPlaced = safeList(block.placedImageAssetIds());
                    List<Long> originalAssociated = safeList(block.associatedImageAssetIds());
                    List<Long> selectedPlaced = filterSelected(originalPlaced, selectedAssetIds);
                    List<Long> selectedAssociated = filterSelected(originalAssociated, selectedAssetIds);
                    List<Long> selectedNeighboring = filterSelected(
                            safeList(block.neighboringImageAssetIds()),
                            selectedAssetIds
                    );
                    boolean imagesOmitted = java.util.stream.Stream
                            .concat(originalPlaced.stream(), originalAssociated.stream())
                            .filter(Objects::nonNull)
                            .anyMatch(assetId -> !selectedAssetIds.contains(assetId));

                    return new ReviewBlockContext(
                            block.blockOrder(),
                            block.instanceKey(),
                            block.templateKey(),
                            block.templateType(),
                            block.textSlots(),
                            selectedPlaced,
                            selectedAssociated,
                            selectedNeighboring,
                            imagesOmitted
                    );
                })
                .toList();
    }

    private List<Long> filterSelected(List<Long> assetIds, Set<Long> selectedAssetIds) {
        return assetIds.stream()
                .filter(Objects::nonNull)
                .filter(selectedAssetIds::contains)
                .toList();
    }

    private ReviewImage processImage(Long assetId, ReviewAssetSource source) {
        try {
            if (source == null) {
                throw new IOException("Image metadata is missing.");
            }

            byte[] jpegBytes = getOrProcessImage(source);
            return new ReviewImage(
                    assetId,
                    source.assetRole(),
                    source.description(),
                    JPEG_MEDIA_TYPE,
                    Base64.getEncoder().encodeToString(jpegBytes)
            );
        } catch (ReviewImageProcessingException e) {
            log.error("Review image processing failed. assetId={}", assetId, e.getCause());
            throw new IllegalStateException("Review image processing failed.", e.getCause());
        } catch (Exception e) {
            log.error("Review image processing failed. assetId={}", assetId, e);
            throw new IllegalStateException("검수 이미지를 처리할 수 없습니다.", e);
        }
    }

    private byte[] getOrProcessImage(ReviewAssetSource source) {
        String cacheKey = cacheKey(source.objectName());
        byte[] cached = reviewImageCache.getIfPresent(cacheKey);
        if (cached != null) {
            meterRegistry.counter("review.image.cache", "result", "hit").increment();
            return cached;
        }

        return reviewImageCache.get(cacheKey, ignored -> {
            meterRegistry.counter("review.image.cache", "result", "miss").increment();
            return downloadAndProcessImage(source.objectName());
        });
    }

    private byte[] downloadAndProcessImage(String objectName) {
        try {
            byte[] originalBytes = gcsService.download(objectName);
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(originalBytes));
            if (decoded == null) {
                throw new IOException("No ImageIO reader could decode the image.");
            }

            BufferedImage normalized = resizeAndFlatten(decoded);
            return encodeJpeg(normalized);
        } catch (IOException e) {
            throw new ReviewImageProcessingException(e);
        }
    }

    private String cacheKey(String objectName) {
        int maxEdge = positiveOrDefault(claudeProperties.getReviewImageMaxEdge(), 512);
        return objectName + '\0' + maxEdge + '\0' + JPEG_QUALITY;
    }

    private BufferedImage resizeAndFlatten(BufferedImage source) {
        int maxEdge = positiveOrDefault(claudeProperties.getReviewImageMaxEdge(), 512);
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        double scale = Math.min(1.0, (double) maxEdge / Math.max(sourceWidth, sourceHeight));
        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));

        BufferedImage normalized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = normalized.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC
            );
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return normalized;
    }

    private byte[] encodeJpeg(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("JPEG writer is not available.");
        }

        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            if (imageOutput == null) {
                throw new IOException("JPEG output stream could not be created.");
            }

            writer.setOutput(imageOutput);
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            if (writeParam.canWriteCompressed()) {
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), writeParam);
            imageOutput.flush();
            return output.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    public record ProcessResult(
            List<ReviewBlockContext> blocks,
            List<ReviewImage> images
    ) {
    }

    private record ImageCandidate(
            Long assetId,
            ImagePriority priority,
            Integer blockOrder,
            long sequence
    ) {
    }

    private enum ImagePriority {
        DIRECT,
        NEIGHBORING
    }

    private static final class ReviewImageProcessingException extends RuntimeException {

        private ReviewImageProcessingException(IOException cause) {
            super(cause);
        }
    }
}
