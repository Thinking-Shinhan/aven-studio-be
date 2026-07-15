package kr.co.daeun.avenstudiobe.domain.imagegeneration.controller;

import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ImageGenerationJobCreateReqDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.dto.ImageGenerationJobRespDto;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.service.ImageGenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageGenerationControllerTest {

    @Test
    void returnsAcceptedWhenJobIsQueued() {
        ImageGenerationService service = mock(ImageGenerationService.class);
        Authentication authentication = mock(Authentication.class);
        ImageGenerationJobCreateReqDto request = new ImageGenerationJobCreateReqDto();
        ImageGenerationJobRespDto response = mock(ImageGenerationJobRespDto.class);
        when(authentication.getName()).thenReturn("tester");
        when(service.createJob(request, "tester")).thenReturn(response);
        ImageGenerationController controller = new ImageGenerationController(service);

        ResponseEntity<ImageGenerationJobRespDto> result = controller.createJob(request, authentication);

        assertEquals(HttpStatus.ACCEPTED, result.getStatusCode());
        assertEquals(response, result.getBody());
    }
}
