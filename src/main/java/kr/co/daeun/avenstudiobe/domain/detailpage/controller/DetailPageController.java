package kr.co.daeun.avenstudiobe.domain.detailpage.controller;

import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageInitRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.service.DetailPageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/detail-pages")
public class DetailPageController {

    private final DetailPageService detailPageService;

    @GetMapping("/init")
    public ResponseEntity<DetailPageInitRespDto> getInitData(
            @RequestParam Long productId
    ) {
        DetailPageInitRespDto response = detailPageService.getInitData(productId);
        return ResponseEntity.ok(response);
    }
}
