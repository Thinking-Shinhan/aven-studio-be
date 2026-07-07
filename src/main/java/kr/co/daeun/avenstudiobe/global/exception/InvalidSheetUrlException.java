package kr.co.daeun.avenstudiobe.global.exception;

public class InvalidSheetUrlException extends RuntimeException {
    public InvalidSheetUrlException() {
        super("유효하지 않은 구글 스프레드시트 URL입니다.");
    }
}
