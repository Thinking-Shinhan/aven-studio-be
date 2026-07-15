package kr.co.daeun.avenstudiobe.global.exception;

public class DetailPageNotFoundException extends RuntimeException {

    public DetailPageNotFoundException() {
        super("존재하지 않는 상세페이지입니다.");
    }
}
