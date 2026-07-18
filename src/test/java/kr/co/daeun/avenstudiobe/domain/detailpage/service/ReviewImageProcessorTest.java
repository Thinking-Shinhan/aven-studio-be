package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto.ReviewTextSlotReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ReviewAssetSource;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ReviewBlockContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ReviewImage;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeProperties;
import kr.co.daeun.avenstudiobe.global.storage.GcsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ReviewImageProcessorTest {

    private static final byte[] ACTUAL_WEBP = Base64.getDecoder().decode(
            "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA"
    );

    private final GcsService gcsService = mock(GcsService.class);
    private final ClaudeProperties properties = new ClaudeProperties();
    private final ReviewImageProcessor processor = new ReviewImageProcessor(gcsService, properties);

    @BeforeEach
    void setUp() {
        properties.setReviewMaxImages(12);
        properties.setReviewImageMaxEdge(512);
    }

    @Test
    void treatsAssociatedAndPlacedImagesAsDirectEvidenceBeforeNeighboringImages() throws IOException {
        properties.setReviewMaxImages(1);
        byte[] png = png(8, 4, false);
        when(gcsService.download("asset-20.png")).thenReturn(png);

        ReviewImageProcessor.ProcessResult result = processor.process(
                List.of(
                        block(1, List.of(), List.of(), List.of(10L)),
                        block(2, List.of(), List.of(20L), List.of()),
                        block(3, List.of(30L), List.of(), List.of())
                ),
                List.of(
                        source(10L, "image/png"),
                        source(20L, "image/png"),
                        source(30L, "image/png")
                )
        );

        assertEquals(List.of(20L), result.images().stream().map(ReviewImage::assetId).toList());
        assertEquals(List.of(), result.blocks().get(0).neighboringImageAssetIds());
        assertFalse(result.blocks().get(0).imagesOmitted());
        assertEquals(List.of(20L), result.blocks().get(1).associatedImageAssetIds());
        assertFalse(result.blocks().get(1).imagesOmitted());
        assertEquals(List.of(), result.blocks().get(2).placedImageAssetIds());
        assertTrue(result.blocks().get(2).imagesOmitted());
        verify(gcsService, never()).download("asset-10.png");
        verify(gcsService, never()).download("asset-30.png");
    }

    @Test
    void removesPlacedImagesPastTheLimitAndMarksOnlyPlacedOmission() throws IOException {
        properties.setReviewMaxImages(2);
        byte[] png = png(8, 4, false);
        when(gcsService.download("asset-10.png")).thenReturn(png);
        when(gcsService.download("asset-11.png")).thenReturn(png);

        ReviewImageProcessor.ProcessResult result = processor.process(
                List.of(block(1, List.of(10L, 11L, 12L), List.of(99L))),
                List.of(
                        source(10L, "image/png"),
                        source(11L, "image/png"),
                        source(12L, "image/png"),
                        source(99L, "image/png")
                )
        );

        assertEquals(List.of(10L, 11L), result.images().stream().map(ReviewImage::assetId).toList());
        assertEquals(List.of(10L, 11L), result.blocks().get(0).placedImageAssetIds());
        assertEquals(List.of(), result.blocks().get(0).neighboringImageAssetIds());
        assertTrue(result.blocks().get(0).imagesOmitted());
    }

    @Test
    void marksOmissionWhenAnAssociatedDirectImageExceedsTheLimit() throws IOException {
        properties.setReviewMaxImages(1);
        when(gcsService.download("asset-10.png")).thenReturn(png(8, 4, false));

        ReviewImageProcessor.ProcessResult result = processor.process(
                List.of(block(1, List.of(), List.of(10L, 11L), List.of(99L))),
                List.of(
                        source(10L, "image/png"),
                        source(11L, "image/png"),
                        source(99L, "image/png")
                )
        );

        assertEquals(List.of(10L), result.images().stream().map(ReviewImage::assetId).toList());
        assertEquals(List.of(10L), result.blocks().get(0).associatedImageAssetIds());
        assertEquals(List.of(), result.blocks().get(0).neighboringImageAssetIds());
        assertTrue(result.blocks().get(0).imagesOmitted());
    }

    @Test
    void resizesWithoutCroppingFlattensAlphaToWhiteAndEncodesJpeg() throws IOException {
        byte[] transparentPng = png(1000, 500, true);
        when(gcsService.download("asset-1.png")).thenReturn(transparentPng);

        ReviewImage image = processor.process(
                List.of(block(1, List.of(1L), List.of())),
                List.of(source(1L, "image/png"))
        ).images().get(0);

        assertEquals("image/jpeg", image.mediaType());
        BufferedImage decoded = decodeBase64Image(image.base64Data());
        assertEquals(512, decoded.getWidth());
        assertEquals(256, decoded.getHeight());
        Color flattenedBackground = new Color(decoded.getRGB(500, 128));
        assertTrue(flattenedBackground.getRed() > 240);
        assertTrue(flattenedBackground.getGreen() > 240);
        assertTrue(flattenedBackground.getBlue() > 240);
    }

    @Test
    void doesNotUpscaleSmallImages() throws IOException {
        when(gcsService.download("asset-1.png")).thenReturn(png(100, 50, false));

        ReviewImage image = processor.process(
                List.of(block(1, List.of(1L), List.of())),
                List.of(source(1L, "image/png"))
        ).images().get(0);

        BufferedImage decoded = decodeBase64Image(image.base64Data());
        assertEquals(100, decoded.getWidth());
        assertEquals(50, decoded.getHeight());
    }

    @Test
    void failsInsteadOfSkippingWhenImageIoCannotDecode(CapturedOutput output) {
        when(gcsService.download("asset-1.png")).thenReturn(new byte[] {1, 2, 3, 4});

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> processor.process(
                        List.of(block(1, List.of(1L), List.of())),
                        List.of(source(1L, "image/png"))
                )
        );
        assertInstanceOf(IOException.class, exception.getCause());
        assertTrue(output.getOut().contains("assetId=1"));
        assertTrue(output.getOut().contains("No ImageIO reader could decode the image."));
        assertFalse(output.getOut().contains("asset-1.png"));
        assertFalse(output.getOut().contains("AQIDBA=="));
    }

    @Test
    void twelveMonkeysActuallyDecodesWebpAndProcessorNormalizesItToJpeg() throws IOException {
        var webpReaders = ImageIO.getImageReadersByFormatName("webp");
        assertTrue(webpReaders.hasNext());
        var webpReader = webpReaders.next();
        assertTrue(webpReader.getClass().getName().startsWith("com.twelvemonkeys."));
        webpReader.dispose();

        BufferedImage decodedWebp = ImageIO.read(new ByteArrayInputStream(ACTUAL_WEBP));
        assertNotNull(decodedWebp);
        assertEquals(1, decodedWebp.getWidth());
        assertEquals(1, decodedWebp.getHeight());

        when(gcsService.download("asset-1.webp")).thenReturn(ACTUAL_WEBP);
        ReviewImage image = processor.process(
                List.of(block(1, List.of(1L), List.of())),
                List.of(source(1L, "image/webp"))
        ).images().get(0);

        assertEquals("image/jpeg", image.mediaType());
        assertNotNull(decodeBase64Image(image.base64Data()));
    }

    private ReviewBlockContext block(
            int blockOrder,
            List<Long> placedImageAssetIds,
            List<Long> neighboringImageAssetIds
    ) {
        return block(blockOrder, placedImageAssetIds, List.of(), neighboringImageAssetIds);
    }

    private ReviewBlockContext block(
            int blockOrder,
            List<Long> placedImageAssetIds,
            List<Long> associatedImageAssetIds,
            List<Long> neighboringImageAssetIds
    ) {
        return new ReviewBlockContext(
                blockOrder,
                "block-" + blockOrder,
                "template-" + blockOrder,
                "MIXED",
                List.of(new ReviewTextSlotReqDto("copy", 1, "BODY", 100, "current")),
                placedImageAssetIds,
                associatedImageAssetIds,
                neighboringImageAssetIds,
                false
        );
    }

    private ReviewAssetSource source(Long assetId, String contentType) {
        String extension = "image/webp".equals(contentType) ? ".webp" : ".png";
        return new ReviewAssetSource(
                assetId,
                "asset-" + assetId + extension,
                contentType,
                "MODEL_CUT",
                "description"
        );
    }

    private byte[] png(int width, int height, boolean transparentBackground) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            if (!transparentBackground) {
                graphics.setColor(Color.BLUE);
                graphics.fillRect(0, 0, width, height);
            } else {
                graphics.setColor(Color.RED);
                graphics.fillRect(0, 0, width / 2, height);
            }
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
        return output.toByteArray();
    }

    private BufferedImage decodeBase64Image(String base64Data) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64Data)));
    }
}
