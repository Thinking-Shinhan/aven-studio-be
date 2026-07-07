package kr.co.daeun.avenstudiobe.global.exception;

public class SheetConnectionNotFoundException extends RuntimeException {
    public SheetConnectionNotFoundException() {
        super("시트 연동 정보를 찾을 수 없습니다.");
    }
}
