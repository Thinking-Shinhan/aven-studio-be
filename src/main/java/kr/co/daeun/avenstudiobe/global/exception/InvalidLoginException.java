package kr.co.daeun.avenstudiobe.global.exception;

public class InvalidLoginException extends RuntimeException {
    public InvalidLoginException() {
        super("아이디 또는 비밀번호가 틀렸습니다.");
    }
}