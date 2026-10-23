package kr.co.daeun.avenstudiobe.domain.imagegeneration.service;

import kr.co.daeun.avenstudiobe.integration.google.GoogleAiImageProvider;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AspectRatioImageCropperTest {

    private final AspectRatioImageCropper cropper = new AspectRatioImageCropper();

    @Test
    void rejectsMissingAspectRatio() {
        GoogleAiImageProvider.GeneratedImage source = image(1344, 768);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> cropper.cropToAspectRatio(source, null)
        );

        assertEquals("크롭할 이미지 비율이 없습니다.", exception.getMessage());
    }

    @Test
    void cropsLandscapeImageToNineBySixteen() throws Exception {
        GoogleAiImageProvider.GeneratedImage source = image(1344, 768);

        AspectRatioImageCropper.CropResult result = cropper.cropToAspectRatio(source, "9:16");

        assertTrue(result.cropped());
        assertEquals(432, result.image().width());
        assertEquals(768, result.image().height());
        assertEncodedDimensions(result.image(), 432, 768);
    }

    @Test
    void cropsLandscapeImageToFourByFive() throws Exception {
        GoogleAiImageProvider.GeneratedImage source = image(1344, 768);

        AspectRatioImageCropper.CropResult result = cropper.cropToAspectRatio(source, "4:5");

        assertTrue(result.cropped());
        assertEquals(612, result.image().width());
        assertEquals(765, result.image().height());
        assertEncodedDimensions(result.image(), 612, 765);
    }

    @Test
    void cropsPortraitImageToSixteenByNine() throws Exception {
        GoogleAiImageProvider.GeneratedImage source = image(768, 1344);

        AspectRatioImageCropper.CropResult result = cropper.cropToAspectRatio(source, "16:9");

        assertTrue(result.cropped());
        assertEquals(768, result.image().width());
        assertEquals(432, result.image().height());
        assertEncodedDimensions(result.image(), 768, 432);
    }

    @Test
    void keepsMatchingImageWithoutReencoding() {
        GoogleAiImageProvider.GeneratedImage source = image(900, 1200);

        AspectRatioImageCropper.CropResult result = cropper.cropToAspectRatio(source, "3:4");

        assertFalse(result.cropped());
        assertEquals(source, result.image());
        assertArrayEquals(source.bytes(), result.image().bytes());
    }

    private GoogleAiImageProvider.GeneratedImage image(int width, int height) {
        try {
            BufferedImage source = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(source, "png", output);
            return new GoogleAiImageProvider.GeneratedImage(
                    output.toByteArray(),
                    "image/png",
                    width,
                    height
            );
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertEncodedDimensions(
            GoogleAiImageProvider.GeneratedImage image,
            int expectedWidth,
            int expectedHeight
    ) throws IOException {
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image.bytes()));
        assertEquals(expectedWidth, decoded.getWidth());
        assertEquals(expectedHeight, decoded.getHeight());
    }
}
