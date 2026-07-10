package kr.co.daeun.avenstudiobe.domain.detailpage.controller;

import jakarta.validation.Valid;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoImagePlacementReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoImagePlacementRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.service.AutoImagePlacementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/detail-pages")
public class DetailPageAutoPlacementController {

    private final AutoImagePlacementService autoImagePlacementService;

    @PostMapping({"/auto-place-image"})
    public ResponseEntity<AutoImagePlacementRespDto> autoPlace(
            @Valid @RequestBody AutoImagePlacementReqDto request
    ) {
        AutoImagePlacementRespDto response = autoImagePlacementService.autoPlace(request);
        return ResponseEntity.ok(response);
    }
}
