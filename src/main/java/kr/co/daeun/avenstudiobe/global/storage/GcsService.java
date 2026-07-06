package kr.co.daeun.avenstudiobe.global.storage;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
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

        if (contentType == null ||
                !(contentType.equals("image/jpeg")
                        || contentType.equals("image/png")
                        || contentType.equals("image/webp"))) {
            throw new IllegalArgumentException("jpg, png, webp 이미지만 업로드할 수 있습니다.");
        }
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