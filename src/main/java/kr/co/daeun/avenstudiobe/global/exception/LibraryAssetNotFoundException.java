package kr.co.daeun.avenstudiobe.global.exception;

public class LibraryAssetNotFoundException extends RuntimeException {

    public LibraryAssetNotFoundException() {
        super("존재하지 않는 라이브러리 이미지입니다.");
    }
}
