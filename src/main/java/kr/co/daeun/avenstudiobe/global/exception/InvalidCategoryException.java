package kr.co.daeun.avenstudiobe.global.exception;

public class InvalidCategoryException extends RuntimeException {

    public InvalidCategoryException() {
        super("유효하지 않은 카테고리입니다.");
    }
}
