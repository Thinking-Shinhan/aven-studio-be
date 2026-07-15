package kr.co.daeun.avenstudiobe.global.exception;

public class ImageGenerationQueueFullException extends RuntimeException {

    public ImageGenerationQueueFullException() {
        super("이미지 생성 요청이 많습니다. 잠시 후 다시 시도해주세요.");
    }
}
