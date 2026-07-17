package kr.co.daeun.avenstudiobe.domain.imagegeneration.service;

import kr.co.daeun.avenstudiobe.integration.google.GoogleAiImageProvider;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class AspectRatioImageCropper {

    private static final double ASPECT_RATIO_TOLERANCE = 0.01;

    public CropResult cropToAspectRatio(
            GoogleAiImageProvider.GeneratedImage image,
            String requestedAspectRatio
    ) {
        if (requestedAspectRatio == null || requestedAspectRatio.isBlank()) {
            return new CropResult(image, false);
        }

        AspectRatio targetRatio = parseAspectRatio(requestedAspectRatio);
        double expectedRatio = targetRatio.value();
        double actualRatio = (double) image.width() / image.height();
        double relativeError = Math.abs(actualRatio - expectedRatio) / expectedRatio;
        if (relativeError <= ASPECT_RATIO_TOLERANCE) {
            return new CropResult(image, false);
        }

        BufferedImage source = readImage(image.bytes());
        int scale = Math.min(
                source.getWidth() / targetRatio.width(),
                source.getHeight() / targetRatio.height()
        );
        if (scale < 1) {
            throw new IllegalStateException(
                    "요청 비율로 이미지를 자를 수 없습니다: " + requestedAspectRatio
            );
        }
        int cropWidth = targetRatio.width() * scale;
        int cropHeight = targetRatio.height() * scale;
        int cropX = (source.getWidth() - cropWidth) / 2;
        int cropY = (source.getHeight() - cropHeight) / 2;

        BufferedImage cropped = source.getSubimage(cropX, cropY, cropWidth, cropHeight);
        byte[] croppedBytes = writeImage(cropped, image.contentType());
        GoogleAiImageProvider.GeneratedImage corrected =
                new GoogleAiImageProvider.GeneratedImage(
                        croppedBytes,
                        image.contentType(),
                        cropWidth,
                        cropHeight
                );
        return new CropResult(corrected, true);
    }

    private AspectRatio parseAspectRatio(String requestedAspectRatio) {
        String[] ratioParts = requestedAspectRatio.split(":");
        if (ratioParts.length != 2) {
            throw new IllegalArgumentException("지원하지 않는 이미지 비율 형식입니다: " + requestedAspectRatio);
        }

        try {
            int width = Integer.parseInt(ratioParts[0]);
            int height = Integer.parseInt(ratioParts[1]);
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException(
                        "이미지 비율은 0보다 커야 합니다: " + requestedAspectRatio
                );
            }
            int divisor = greatestCommonDivisor(width, height);
            return new AspectRatio(width / divisor, height / divisor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "지원하지 않는 이미지 비율 형식입니다: " + requestedAspectRatio,
                    e
            );
        }
    }

    private int greatestCommonDivisor(int first, int second) {
        int left = first;
        int right = second;
        while (right != 0) {
            int remainder = left % right;
            left = right;
            right = remainder;
        }
        return left;
    }

    private BufferedImage readImage(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new IllegalStateException("생성 이미지를 읽을 수 없습니다.");
            }
            return image;
        } catch (IOException e) {
            throw new IllegalStateException("생성 이미지 디코딩에 실패했습니다.", e);
        }
    }

    private byte[] writeImage(BufferedImage image, String contentType) {
        String format = switch (contentType) {
            case "image/jpeg", "image/jpg" -> "jpeg";
            case "image/png" -> "png";
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 생성 이미지 형식입니다: " + contentType
            );
        };

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, format, output)) {
                throw new IllegalStateException("생성 이미지 인코더를 찾을 수 없습니다: " + format);
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("생성 이미지 인코딩에 실패했습니다.", e);
        }
    }

    public record CropResult(
            GoogleAiImageProvider.GeneratedImage image,
            boolean cropped
    ) {
    }

    private record AspectRatio(int width, int height) {

        private double value() {
            return (double) width / height;
        }
    }
}
