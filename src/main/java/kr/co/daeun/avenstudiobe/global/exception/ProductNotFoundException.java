package kr.co.daeun.avenstudiobe.global.exception;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException() {
        super("상품 정보를 찾을 수 없습니다.");
    }
}
