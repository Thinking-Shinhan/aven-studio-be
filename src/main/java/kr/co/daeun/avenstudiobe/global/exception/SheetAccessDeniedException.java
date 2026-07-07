package kr.co.daeun.avenstudiobe.global.exception;

public class SheetAccessDeniedException extends RuntimeException {
    public SheetAccessDeniedException(Throwable cause) {
        super("스프레드시트에 대한 접근 권한이 없습니다. 서비스 계정과 시트 공유 설정을 확인해주세요.", cause);
    }
}
