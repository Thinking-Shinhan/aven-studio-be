package kr.co.daeun.avenstudiobe.global.exception;

public class LibraryFolderNotFoundException extends RuntimeException {

    public LibraryFolderNotFoundException() {
        super("존재하지 않는 이미지 폴더입니다.");
    }
}
