package kr.co.daeun.avenstudiobe.global.exception;

public class BrandNotFoundException extends RuntimeException {
    public BrandNotFoundException() {
        super("존재하지 않는 브랜드입니다.");
    }
}
