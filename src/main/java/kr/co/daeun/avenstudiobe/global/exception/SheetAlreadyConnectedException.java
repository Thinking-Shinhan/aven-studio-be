package kr.co.daeun.avenstudiobe.global.exception;

public class SheetAlreadyConnectedException extends RuntimeException {
    public SheetAlreadyConnectedException() {
        super("이미 연동된 스프레드시트입니다.");
    }
}
