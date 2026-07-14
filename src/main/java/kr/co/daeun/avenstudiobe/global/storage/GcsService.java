package kr.co.daeun.avenstudiobe.global.storage;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import kr.co.daeun.avenstudiobe.global.exception.UnsupportedImageMediaTypeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class GcsService {

    @Value("${gcp.storage.bucket}")
    private String bucketName;

    private final Storage storage = StorageOptions.getDefaultInstance().getService();

    public UploadedFile upload(MultipartFile file, Long productId) {
        validateImage(file);

        try {
            String extension = getExtension(file.getOriginalFilename());
            String objectName = "products/" + productId + "/" + UUID.randomUUID() + extension;

            BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName)
                    .setContentType(file.getContentType())
                    .build();

            storage.create(blobInfo, file.getInputStream());

            String fileUrl = "https://storage.googleapis.com/" + bucketName + "/" + objectName;

            return new UploadedFile(fileUrl, objectName, file.getOriginalFilename(), file.getContentType(), file.getSize());

        } catch (IOException e) {
            throw new RuntimeException("이미지 업로드에 실패했습니다.", e);
        }
    }

    public UploadedFile uploadReferenceAsset(MultipartFile file, String referenceType) {
        validateImage(file);

        try {
            String extension = getExtension(file.getOriginalFilename());
            String normalizedReferenceType = referenceType.toLowerCase();
            String objectName = "reference-assets/" + normalizedReferenceType + "/" + UUID.randomUUID() + extension;

            BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName)
                    .setContentType(file.getContentType())
                    .build();

            storage.create(blobInfo, file.getInputStream());

            String fileUrl = "https://storage.googleapis.com/" + bucketName + "/" + objectName;

            return new UploadedFile(fileUrl, objectName, file.getOriginalFilename(), file.getContentType(), file.getSize());

        } catch (IOException e) {
            throw new RuntimeException("레퍼런스 이미지 업로드에 실패했습니다.", e);
        }
    }

    public UploadedFile uploadGeneratedImage(byte[] bytes, String contentType, Long productId, Long jobId) {
        validateGeneratedImage(bytes, contentType);

        String normalizedContentType = normalizeContentType(contentType);
        String extension = getExtensionByContentType(normalizedContentType);
        String objectName = "generated/" + productId + "/" + jobId + "/" + UUID.randomUUID() + extension;

        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName)
                .setContentType(normalizedContentType)
                .build();

        storage.create(blobInfo, bytes);

        String fileUrl = "https://storage.googleapis.com/" + bucketName + "/" + objectName;
        String originalFilename = "generated-" + jobId + extension;

        return new UploadedFile(fileUrl, objectName, originalFilename, normalizedContentType, bytes.length);
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new UnsupportedImageMediaTypeException();
        }

        String extension = filename.substring(filename.lastIndexOf(".")).toLowerCase();

        if (!extension.equals(".jpg")
                && !extension.equals(".jpeg")
                && !extension.equals(".png")
                && !extension.equals(".webp")) {
            throw new UnsupportedImageMediaTypeException();
        }

        return extension;
    }

    public record UploadedFile(
            String fileUrl,
            String objectName,
            String originalFilename,
            String contentType,
            long size
    ) {}

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("필수 입력값이 누락되었거나 이미지 파일이 올바르지 않습니다.");
        }

        String contentType = normalizeUploadedContentType(file.getContentType());
        String extension = getExtension(file.getOriginalFilename());
        String detectedContentType = detectImageContentType(file);

        if (!contentType.equals(detectedContentType)
                || !extensionMatchesContentType(extension, detectedContentType)) {
            throw new IllegalArgumentException("필수 입력값이 누락되었거나 이미지 파일이 올바르지 않습니다.");
        }
    }

    private String normalizeUploadedContentType(String contentType) {
        if ("image/png".equals(contentType) || "image/x-png".equals(contentType)) {
            return "image/png";
        }

        if ("image/jpeg".equals(contentType) || "image/jpg".equals(contentType)) {
            return "image/jpeg";
        }

        if ("image/webp".equals(contentType)) {
            return "image/webp";
        }

        throw new UnsupportedImageMediaTypeException();
    }

    private String detectImageContentType(MultipartFile file) {
        try (var inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(12);

            if (isPng(header)) {
                return "image/png";
            }

            if (isJpeg(header)) {
                return "image/jpeg";
            }

            if (isWebp(header)) {
                return "image/webp";
            }

            throw invalidImageFile();
        } catch (IOException e) {
            throw new IllegalArgumentException("필수 입력값이 누락되었거나 이미지 파일이 올바르지 않습니다.", e);
        }
    }

    private boolean extensionMatchesContentType(String extension, String contentType) {
        return switch (contentType) {
            case "image/png" -> extension.equals(".png");
            case "image/jpeg" -> extension.equals(".jpg") || extension.equals(".jpeg");
            case "image/webp" -> extension.equals(".webp");
            default -> false;
        };
    }

    private boolean isPng(byte[] header) {
        return header.length >= 8
                && (header[0] & 0xFF) == 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A;
    }

    private boolean isJpeg(byte[] header) {
        return header.length >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF;
    }

    private boolean isWebp(byte[] header) {
        return header.length >= 12
                && header[0] == 'R'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == 'F'
                && header[8] == 'W'
                && header[9] == 'E'
                && header[10] == 'B'
                && header[11] == 'P';
    }

    private IllegalArgumentException invalidImageFile() {
        return new IllegalArgumentException("필수 입력값이 누락되었거나 이미지 파일이 올바르지 않습니다.");
    }

    private void validateGeneratedImage(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("생성 이미지 데이터가 비어 있습니다.");
        }

        normalizeContentType(contentType);
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "image/png";
        }

        if (contentType.equals("image/png") || contentType.equals("image/x-png")) {
            return "image/png";
        }

        if (contentType.equals("image/jpeg") || contentType.equals("image/jpg")) {
            return "image/jpeg";
        }

        if (contentType.equals("image/webp")) {
            return "image/webp";
        }

        throw new IllegalArgumentException("jpg, png, webp 이미지만 저장할 수 있습니다.");
    }

    private String getExtensionByContentType(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            default -> ".png";
        };
    }

    public void delete(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException("삭제할 파일 경로가 없습니다.");
        }

        storage.delete(bucketName, objectName);
    }

    public void deleteQuietly(String objectName) {
        try {
            delete(objectName);
        } catch (RuntimeException ignored) {
        }
    }

    public String generateSignedUrl(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException("서명할 파일 경로가 없습니다.");
        }

        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName).build();

        URL signedUrl = storage.signUrl(
                blobInfo,
                6,
                TimeUnit.HOURS,
                Storage.SignUrlOption.withV4Signature()
        );

        return signedUrl.toString();
    }

    public byte[] download(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException("다운로드할 파일 경로가 없습니다.");
        }

        Blob blob = storage.get(bucketName, objectName);
        if (blob == null || !blob.exists()) {
            throw new IllegalArgumentException("GCS 파일을 찾을 수 없습니다.");
        }

        return blob.getContent();
    }

}
