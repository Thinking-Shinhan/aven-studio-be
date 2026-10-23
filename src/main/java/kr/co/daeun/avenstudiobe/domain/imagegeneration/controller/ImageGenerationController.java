package kr.co.daeun.avenstudiobe.domain.imagegeneration.controller;

import jakarta.validation.Valid;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ImageGenerationJobCreateReqDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ImageGenerationJobRespDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.service.ImageGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/image-generation/jobs")
public class ImageGenerationController {

    private final ImageGenerationService imageGenerationService;

    @PostMapping
    public ResponseEntity<ImageGenerationJobRespDto> createJob(
            @RequestBody @Valid ImageGenerationJobCreateReqDto request,
            Authentication authentication
    ) {
        ImageGenerationJobRespDto response = imageGenerationService.createJob(request, authentication.getName());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ImageGenerationJobRespDto> getJob(
            @PathVariable Long jobId,
            Authentication authentication
    ) {
        ImageGenerationJobRespDto response = imageGenerationService.getJob(jobId, authentication.getName());

        return ResponseEntity.ok(response);
    }
}
