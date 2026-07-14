package kr.co.daeun.avenstudiobe.global.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void returnsUnsupportedMediaTypeForUnsupportedImage() {
        ResponseEntity<ErrorResponse> response = handler.handleUnsupportedImageMediaType(
                new UnsupportedImageMediaTypeException()
        );

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", response.getBody().getError().getCode());
    }

    @Test
    void returnsFileSizeExceededForOversizedUpload() {
        ResponseEntity<ErrorResponse> response = handler.handleMaxUploadSize(
                new MaxUploadSizeExceededException(10L)
        );

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertEquals("FILE_SIZE_EXCEEDED", response.getBody().getError().getCode());
    }

    @Test
    void returnsBadRequestWhenFilePartIsMissing() {
        ResponseEntity<ErrorResponse> response = handler.handleMissingRequestPart(
                new MissingServletRequestPartException("file")
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("BAD_REQUEST", response.getBody().getError().getCode());
    }
}
