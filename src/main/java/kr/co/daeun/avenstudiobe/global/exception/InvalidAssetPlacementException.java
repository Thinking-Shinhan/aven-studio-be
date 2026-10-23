package kr.co.daeun.avenstudiobe.global.exception;

public class InvalidAssetPlacementException extends IllegalArgumentException {

    public InvalidAssetPlacementException() {
        super("요청한 이미지가 존재하지 않거나 상품에 속하지 않습니다.");
    }
}
