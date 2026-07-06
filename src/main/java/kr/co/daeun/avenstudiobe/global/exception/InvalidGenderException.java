package kr.co.daeun.avenstudiobe.global.exception;

public class InvalidGenderException extends RuntimeException {

    public InvalidGenderException() {
        super("유효하지 않은 성별입니다.");
    }
}