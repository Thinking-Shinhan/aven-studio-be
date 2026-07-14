package kr.co.daeun.avenstudiobe.global.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import kr.co.daeun.avenstudiobe.global.exception.UnsupportedImageMediaTypeException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class GcsServiceTest {

    private final GcsService service = new GcsService();

    @Test
    void rejectsUnsupportedImageType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "detail.gif",
                "image/gif",
                new byte[]{'G', 'I', 'F'}
        );

        UnsupportedImageMediaTypeException exception = assertThrows(
                UnsupportedImageMediaTypeException.class,
                () -> service.upload(file, 1L)
        );

        assertEquals("png, jpg, jpeg, webp 이미지만 업로드할 수 있습니다.", exception.getMessage());
    }

    @Test
    void rejectsEmptyImageAsBadRequest() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "detail.png",
                "image/png",
                new byte[0]
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.upload(file, 1L)
        );

        assertEquals("필수 입력값이 누락되었거나 이미지 파일이 올바르지 않습니다.", exception.getMessage());
    }

    @Test
    void rejectsCorruptedImageAsBadRequest() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "detail.png",
                "image/png",
                "not-a-png".getBytes()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.upload(file, 1L)
        );

        assertEquals("필수 입력값이 누락되었거나 이미지 파일이 올바르지 않습니다.", exception.getMessage());
    }
}
