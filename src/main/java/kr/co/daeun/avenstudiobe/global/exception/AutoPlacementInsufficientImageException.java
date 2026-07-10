package kr.co.daeun.avenstudiobe.global.exception;

public class AutoPlacementInsufficientImageException extends RuntimeException {

    public AutoPlacementInsufficientImageException() {
        super("자동 배치에 사용할 수 있는 이미지가 부족합니다.");
    }
}
