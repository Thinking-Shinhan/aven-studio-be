package kr.co.daeun.avenstudiobe.domain.imagegeneration.controller;

import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ImageGenerationOptionsRespDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ReferenceAssetListRespDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ShotTemplateListRespDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.service.ImageGenerationOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/image-generation")
public class ImageGenerationOptionController {

    private final ImageGenerationOptionService imageGenerationOptionService;

    @GetMapping("/options")
    public ResponseEntity<ImageGenerationOptionsRespDto> getOptions() {
        ImageGenerationOptionsRespDto options = imageGenerationOptionService.getOptions();

        return ResponseEntity.ok().body(options);
    }

    @GetMapping("/shot-templates")
    public ResponseEntity<ShotTemplateListRespDto> getShotTemplates(
            @RequestParam(defaultValue = "PRODUCT_CUT") String cutType
    ) {
        ShotTemplateListRespDto shotTemplates = imageGenerationOptionService.getShotTemplates(cutType);

        return ResponseEntity.ok().body(shotTemplates);
    }

    @GetMapping("/reference-assets")
    public ResponseEntity<ReferenceAssetListRespDto> getReferenceAssets(
            @RequestParam(required = false) String assetType
    ) {
        ReferenceAssetListRespDto referenceAssets = imageGenerationOptionService.getReferenceAssets(assetType);

        return ResponseEntity.ok().body(referenceAssets);
    }
}
