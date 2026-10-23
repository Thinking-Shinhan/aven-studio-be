package kr.co.daeun.avenstudiobe.global.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeApiException;
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

    @Test
    void mapsClaudeClientErrorToBadGateway() {
        ResponseEntity<ErrorResponse> response = handler.handleClaudeApi(
                new ClaudeApiException(400, "bad request")
        );

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("AI_BAD_GATEWAY", response.getBody().getError().getCode());
    }

    @Test
    void mapsClaudeServerAndNetworkErrorsToServiceUnavailable() {
        ResponseEntity<ErrorResponse> serverResponse = handler.handleClaudeApi(
                new ClaudeApiException(500, "server error")
        );
        ResponseEntity<ErrorResponse> networkResponse = handler.handleClaudeApi(
                new ClaudeApiException("network error", new RuntimeException("connection failed"))
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, serverResponse.getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, networkResponse.getStatusCode());
        assertEquals("AI_SERVICE_UNAVAILABLE", networkResponse.getBody().getError().getCode());
    }

    @Test
    void mapsInvalidReviewResponseStructureToBadGateway() {
        ResponseEntity<ErrorResponse> response = handler.handleIllegalState(
                new IllegalStateException("invalid AI response")
        );

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("BAD_GATEWAY", response.getBody().getError().getCode());
    }
}
