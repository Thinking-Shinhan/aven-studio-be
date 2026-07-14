package kr.co.daeun.avenstudiobe.domain.library.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.daeun.avenstudiobe.domain.library.dto.ImageFolderListRespDto;
import kr.co.daeun.avenstudiobe.domain.library.dto.LibraryAssetDeleteRespDto;
import kr.co.daeun.avenstudiobe.domain.library.dto.LibraryAssetListRespDto;
import kr.co.daeun.avenstudiobe.domain.library.service.LibraryService;
import kr.co.daeun.avenstudiobe.domain.user.entity.User;
import kr.co.daeun.avenstudiobe.domain.user.service.UsersService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/library")
@Tag(name = "Library", description = "생성 이미지 라이브러리 API")
@SecurityRequirement(name = "accessTokenCookie")
public class LibraryController {

    private final LibraryService libraryService;
    private final UsersService usersService;

    @GetMapping("/image-folders")
    @Operation(summary = "이미지 폴더 목록 조회", description = "로그인 브랜드의 상품별 생성 이미지 폴더를 조회합니다.")
    public ResponseEntity<ImageFolderListRespDto> getImageFolders(
            @RequestParam(required = false) String category,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                libraryService.getImageFolders(getBrandId(authentication), category)
        );
    }

    @GetMapping("/image-folders/{productId}/assets")
    @Operation(summary = "폴더별 이미지 조회", description = "선택한 상품에 자동 저장된 생성 이미지를 조회합니다.")
    public ResponseEntity<LibraryAssetListRespDto> getFolderAssets(
            @PathVariable Long productId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                libraryService.getFolderAssets(getBrandId(authentication), productId)
        );
    }

    @DeleteMapping("/assets/{assetId}")
    @Operation(summary = "라이브러리 이미지 삭제", description = "생성 이미지를 GCS에서 삭제하고 라이브러리에서 제외합니다.")
    public ResponseEntity<LibraryAssetDeleteRespDto> deleteAsset(
            @PathVariable Long assetId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                libraryService.deleteAsset(getBrandId(authentication), assetId)
        );
    }

    private Long getBrandId(Authentication authentication) {
        User user = usersService.findByLoginId(authentication.getName());
        return user.getBrand().getId();
    }
}
