package kr.co.daeun.avenstudiobe.domain.detailpage.controller;

import jakarta.validation.Valid;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageAutoFillReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageAutoFillRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageInitRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.service.DetailPageAutoFillService;
import kr.co.daeun.avenstudiobe.domain.detailpage.service.DetailPageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/detail-pages")
public class DetailPageController {

    private final DetailPageService detailPageService;
    private final DetailPageAutoFillService detailPageAutoFillService;

    @GetMapping("/init")
    public ResponseEntity<DetailPageInitRespDto> getInitData(
            @RequestParam Long productId,
            Authentication authentication
    ) {
        DetailPageInitRespDto response = detailPageService.getInitData(productId, authentication);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/auto-fill")
    public ResponseEntity<DetailPageAutoFillRespDto> autoFill(
            @Valid @RequestBody DetailPageAutoFillReqDto request,
            Authentication authentication
    ) {
        DetailPageAutoFillRespDto response = detailPageAutoFillService.autoFill(request, authentication);
        return ResponseEntity.ok(response);
    }
}
