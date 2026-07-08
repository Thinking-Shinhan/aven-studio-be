package kr.co.daeun.avenstudiobe.global.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "에러 응답")
public class ErrorResponse {
    @Schema(description = "에러 상세 정보")
    private ErrorDetail error;

    @Getter
    @AllArgsConstructor
    @Schema(description = "에러 상세 정보")
    public static class ErrorDetail {
        @Schema(description = "에러 코드", example = "INVALID_REQUEST")
        private String code;
        @Schema(description = "에러 메시지", example = "Invalid request.")
        private String message;
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new ErrorDetail(code, message));
    }
}
