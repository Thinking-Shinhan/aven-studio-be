package kr.co.daeun.avenstudiobe.global.storage;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
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
            return "";
        }

        String extension = filename.substring(filename.lastIndexOf(".")).toLowerCase();

        if (!extension.equals(".jpg")
                && !extension.equals(".jpeg")
                && !extension.equals(".png")
                && !extension.equals(".webp")) {
            throw new IllegalArgumentException("허용되지 않는 이미지 확장자입니다.");
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
            throw new IllegalArgumentException("파일이 비어 있습니다.");
        }

        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();

        boolean validContentType = contentType != null &&
                (contentType.equals("image/jpeg")
                        || contentType.equals("image/png")
                        || contentType.equals("image/webp")
                        || contentType.equals("image/x-png"));

        boolean validExtension = filename != null &&
                (filename.toLowerCase().endsWith(".jpg")
                        || filename.toLowerCase().endsWith(".jpeg")
                        || filename.toLowerCase().endsWith(".png")
                        || filename.toLowerCase().endsWith(".webp"));

        if (!validContentType && !validExtension) {
            throw new IllegalArgumentException("jpg, png, webp 이미지만 업로드할 수 있습니다.");
        }
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

}
