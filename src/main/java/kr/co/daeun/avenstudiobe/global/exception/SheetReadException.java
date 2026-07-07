package kr.co.daeun.avenstudiobe.global.exception;

public class SheetReadException extends RuntimeException {
    public SheetReadException() {
        super("스프레드시트를 읽는 중 오류가 발생했습니다.");
    }

    public SheetReadException(Throwable cause) {
        super("스프레드시트를 읽는 중 오류가 발생했습니다.", cause);
    }
}
