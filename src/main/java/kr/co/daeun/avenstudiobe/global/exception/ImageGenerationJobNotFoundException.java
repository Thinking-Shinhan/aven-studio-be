package kr.co.daeun.avenstudiobe.global.exception;

public class ImageGenerationJobNotFoundException extends RuntimeException {

    public ImageGenerationJobNotFoundException() {
        super("이미지 생성 작업을 찾을 수 없습니다.");
    }
}
