package kr.co.daeun.avenstudiobe.global.exception;

public class InvalidCopyGenerationResultException extends IllegalStateException {

    public InvalidCopyGenerationResultException() {
        super("문구 생성 결과가 올바르지 않습니다.");
    }

    public InvalidCopyGenerationResultException(String message) {
        super(message);
    }

    public InvalidCopyGenerationResultException(String message, Throwable cause) {
        super(message, cause);
    }
}
