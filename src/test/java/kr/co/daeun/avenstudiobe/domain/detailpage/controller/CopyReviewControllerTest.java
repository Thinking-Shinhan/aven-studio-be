package kr.co.daeun.avenstudiobe.domain.detailpage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.service.CopyReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class CopyReviewControllerTest {

    @Test
    void exposesReviewCopyEndpointAndForwardsAuthentication() throws NoSuchMethodException {
        CopyReviewService service = mock(CopyReviewService.class);
        CopyReviewController controller = new CopyReviewController(service);
        Authentication authentication = mock(Authentication.class);
        CopyReviewReqDto request = new CopyReviewReqDto(14L, List.of());
        CopyReviewRespDto expected = new CopyReviewRespDto(14L, List.of());
        when(service.review(request, authentication)).thenReturn(expected);

        ResponseEntity<CopyReviewRespDto> response = controller.reviewCopy(request, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
        verify(service).review(request, authentication);

        RequestMapping classMapping = CopyReviewController.class.getAnnotation(RequestMapping.class);
        Method method = CopyReviewController.class.getMethod(
                "reviewCopy", CopyReviewReqDto.class, Authentication.class
        );
        PostMapping methodMapping = method.getAnnotation(PostMapping.class);
        assertEquals("/detail-pages", classMapping.value()[0]);
        assertEquals("/review-copy", methodMapping.value()[0]);
    }
}
