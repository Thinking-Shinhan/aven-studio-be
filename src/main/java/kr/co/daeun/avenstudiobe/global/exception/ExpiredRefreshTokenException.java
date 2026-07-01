package kr.co.daeun.avenstudiobe.global.exception;

public class ExpiredRefreshTokenException extends RuntimeException {

    public ExpiredRefreshTokenException() {
        super("Refresh Token이 만료되었습니다. 다시 로그인해주세요.");
    }
}