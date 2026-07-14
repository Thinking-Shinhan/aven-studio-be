package kr.co.daeun.avenstudiobe.global.exception;

import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ClaudeApiException.class)
    public ResponseEntity<ErrorResponse> handleClaudeApi(ClaudeApiException e) {
        log.error("Claude API request failed", e);

        HttpStatus status = e.getStatusCode() >= 500 || e.getStatusCode() == 0
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.BAD_GATEWAY;
        String code = status == HttpStatus.SERVICE_UNAVAILABLE
                ? "AI_SERVICE_UNAVAILABLE"
                : "AI_BAD_GATEWAY";
        String message = status == HttpStatus.SERVICE_UNAVAILABLE
                ? "AI 문구 생성 서비스를 일시적으로 사용할 수 없습니다."
                : "AI 문구 생성 서비스의 응답을 처리할 수 없습니다.";

        return ResponseEntity
                .status(status)
                .body(ErrorResponse.of(code, message));
    }

    @ExceptionHandler(DuplicateUserIdException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateUserId(DuplicateUserIdException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(AutoPlacementInsufficientImageException.class)
    public ResponseEntity<ErrorResponse> handleAutoPlacementInsufficientImage(
            AutoPlacementInsufficientImageException e
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult()
                .getFieldErrors()
                .get(0)
                .getDefaultMessage();

        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of("BAD_REQUEST", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of("BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestParameter(MissingServletRequestParameterException e) {
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of("BAD_REQUEST", getRequestParameterMessage(e.getParameterName())));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of("BAD_REQUEST", getRequestParameterMessage(e.getName())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of("BAD_REQUEST", "요청 JSON 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        log.error("외부 연동 처리 실패", e);
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of("BAD_GATEWAY", e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("업로드 가능한 크기 또는 multipart 헤더 크기 초과", e);
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponse.of("PAYLOAD_TOO_LARGE", "업로드 가능한 파일 크기 또는 파일명 길이를 초과했습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("서버 내부 오류 발생", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."));
    }

    @ExceptionHandler(InvalidLoginException.class)
    public ResponseEntity<ErrorResponse> handleInvalidLogin(InvalidLoginException e) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("UNAUTHORIZED", e.getMessage()));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidTokenException e) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("UNAUTHORIZED", e.getMessage()));
    }

    @ExceptionHandler(ExpiredRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleExpiredRefreshToken(ExpiredRefreshTokenException e) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("UNAUTHORIZED", e.getMessage()));
    }

    @ExceptionHandler(InvalidCategoryException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCategoryException(InvalidCategoryException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(InvalidGenderException.class)
    public ResponseEntity<ErrorResponse> handleInvalidGenderException(InvalidGenderException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(InvalidSheetUrlException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSheetUrl(InvalidSheetUrlException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(SheetAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleSheetAccessDenied(SheetAccessDeniedException e) {
        log.error("스프레드시트 접근 권한 오류", e);
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("FORBIDDEN", "스프레드시트에 접근할 수 없습니다. 공유 권한을 확인해주세요."));
    }

    @ExceptionHandler(SheetReadException.class)
    public ResponseEntity<ErrorResponse> handleSheetRead(SheetReadException e) {
        log.error("스프레드시트 읽기 실패", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_SERVER_ERROR", "스프레드시트 상품 동기화 중 오류가 발생했습니다."));
    }

    @ExceptionHandler(SheetConnectionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSheetConnectionNotFound(SheetConnectionNotFoundException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(SheetSyncInProgressException.class)
    public ResponseEntity<ErrorResponse> handleSheetSyncInProgress(SheetSyncInProgressException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(BrandNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBrandNotFound(BrandNotFoundException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFound(ProductNotFoundException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler({LibraryFolderNotFoundException.class, LibraryAssetNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleLibraryNotFound(RuntimeException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(SheetAlreadyConnectedException.class)
    public ResponseEntity<ErrorResponse> handleSheetAlreadyConnected(SheetAlreadyConnectedException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CONFLICT", e.getMessage()));
    }

    private String getRequestParameterMessage(String parameterName) {
        if ("productId".equals(parameterName)) {
            return "상품 ID는 필수입니다.";
        }

        return "요청 파라미터 형식이 올바르지 않습니다.";
    }
}
