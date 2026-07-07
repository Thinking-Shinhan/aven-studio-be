package kr.co.daeun.avenstudiobe.global.exception;

public class SheetSyncInProgressException extends RuntimeException {
    public SheetSyncInProgressException() {
        super("이미 동기화가 진행 중입니다.");
    }
}
