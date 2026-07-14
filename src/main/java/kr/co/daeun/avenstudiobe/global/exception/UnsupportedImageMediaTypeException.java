package kr.co.daeun.avenstudiobe.global.exception;

public class UnsupportedImageMediaTypeException extends RuntimeException {

    public UnsupportedImageMediaTypeException() {
        super("png, jpg, jpeg, webp 이미지만 업로드할 수 있습니다.");
    }
}
