package kr.co.daeun.avenstudiobe.domain.brandmood.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.daeun.avenstudiobe.domain.brand.entity.Brand;
import kr.co.daeun.avenstudiobe.domain.brandmood.dto.BrandMoodAnalysisRespDto;
import kr.co.daeun.avenstudiobe.domain.brandmood.dto.BrandMoodAnalyzeReqDto;
import kr.co.daeun.avenstudiobe.domain.brandmood.dto.BrandMoodRespDto;
import kr.co.daeun.avenstudiobe.domain.brandmood.dto.BrandMoodSaveReqDto;
import kr.co.daeun.avenstudiobe.domain.brandmood.repository.BrandMoodRepository;
import kr.co.daeun.avenstudiobe.domain.user.entity.User;
import kr.co.daeun.avenstudiobe.integration.anthropic.AnthropicMoodClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandMoodServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private BrandMoodRepository brandMoodRepository;

    @Mock
    private AnthropicMoodClient anthropicMoodClient;

    @Mock
    private User user;

    @Mock
    private Brand brand;

    private BrandMoodService brandMoodService;

    @BeforeEach
    void setUp() {
        brandMoodService = new BrandMoodService(brandMoodRepository, anthropicMoodClient);
        when(user.getBrand()).thenReturn(brand);
        when(brand.getId()).thenReturn(1L);
    }

    @Test
    void analyzeReturnsDisplayValuesAndPreservesInternalAnalysis() throws Exception {
        when(anthropicMoodClient.analyzeBrandMood(anyString(), anyString()))
                .thenReturn(nestedAnalysisJson("긴 내부 브랜드 요약", "짧은 화면 요약"));

        BrandMoodAnalysisRespDto response = brandMoodService.analyze(request(), null, user);

        assertEquals("AVEN", response.getBrandName());
        assertEquals("짧은 화면 요약", response.getBrandSummary());
        assertEquals(List.of("프리미엄", "차분함"), response.getBrandTone());

        JsonNode raw = OBJECT_MAPPER.readTree(response.getRawAnalysisJson());
        assertEquals("긴 내부 브랜드 요약", raw.path("internalAnalysis").path("brandSummary").asText());
        assertEquals("짧은 화면 요약", raw.path("display").path("brandSummary").asText());
        verify(anthropicMoodClient).analyzeBrandMood(anyString(), anyString());
    }

    @Test
    void analyzeCorrectsOnlyDisplayOnceWhenLimitIsExceeded() throws Exception {
        String tooLong = "가".repeat(221);
        when(anthropicMoodClient.analyzeBrandMood(anyString(), anyString()))
                .thenReturn(nestedAnalysisJson("변경되면 안 되는 내부 분석", tooLong))
                .thenReturn(valuesJson("교정된 화면 요약"));

        BrandMoodAnalysisRespDto response = brandMoodService.analyze(request(), null, user);

        assertEquals("교정된 화면 요약", response.getBrandSummary());
        JsonNode raw = OBJECT_MAPPER.readTree(response.getRawAnalysisJson());
        assertEquals(
                "변경되면 안 되는 내부 분석",
                raw.path("internalAnalysis").path("brandSummary").asText()
        );
        verify(anthropicMoodClient, times(2)).analyzeBrandMood(anyString(), anyString());
    }

    @Test
    void analyzeFailsWhenCorrectedDisplayStillExceedsLimit() {
        String tooLong = "가".repeat(221);
        when(anthropicMoodClient.analyzeBrandMood(anyString(), anyString()))
                .thenReturn(nestedAnalysisJson("내부 분석", tooLong))
                .thenReturn(valuesJson(tooLong));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> brandMoodService.analyze(request(), null, user)
        );

        assertTrue(exception.getMessage().contains("길이 제한"));
    }

    @Test
    void analyzeSupportsUrlAndPdfTogether() {
        MockMultipartFile pdf = new MockMultipartFile(
                "file",
                "guide.pdf",
                "application/pdf",
                "pdf".getBytes(StandardCharsets.UTF_8)
        );
        when(anthropicMoodClient.analyzeBrandMoodWithPdf(anyString(), anyString(), any()))
                .thenReturn(nestedAnalysisJson("PDF 내부 분석", "PDF 화면 요약"));

        BrandMoodAnalysisRespDto response = brandMoodService.analyze(request(), pdf, user);

        assertEquals("PDF 화면 요약", response.getBrandSummary());
        assertEquals(
                "PDF",
                assertDoesNotThrow(() -> OBJECT_MAPPER.readTree(response.getRawAnalysisJson()))
                        .path("sourceType")
                        .asText()
        );
        verify(anthropicMoodClient).analyzeBrandMoodWithPdf(anyString(), anyString(), any());
        verify(anthropicMoodClient, never()).analyzeBrandMood(anyString(), anyString());
    }

    @Test
    void saveRejectsDisplayValuesThatExceedLimits() {
        BrandMoodSaveReqDto request = validSaveRequest();
        request.setBrandTone(List.of(
                "키워드1", "키워드2", "키워드3", "키워드4", "키워드5",
                "키워드6", "키워드7", "키워드8", "키워드9"
        ));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> brandMoodService.save(request, user)
        );

        assertTrue(exception.getMessage().contains("brandTone"));
        verify(brandMoodRepository, never()).save(any());
    }

    @Test
    void saveReportsStringAndJoinedListLimitViolations() {
        BrandMoodSaveReqDto request = validSaveRequest();
        request.setBrandSummary("가".repeat(221));
        request.setDesignPhilosophy("가".repeat(85));
        request.setLighting("가".repeat(26));
        request.setBrandTone(List.of("가".repeat(85)));
        request.setAvoidElements(List.of("가".repeat(101)));
        request.setCustomBrandNote("가".repeat(121));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> brandMoodService.save(request, user)
        );

        assertTrue(exception.getMessage().contains("brandSummary"));
        assertTrue(exception.getMessage().contains("designPhilosophy"));
        assertTrue(exception.getMessage().contains("lighting"));
        assertTrue(exception.getMessage().contains("brandTone"));
        assertTrue(exception.getMessage().contains("avoidElements"));
        assertTrue(exception.getMessage().contains("customBrandNote"));
    }

    @Test
    void saveCountsUnicodeCodePointsInsteadOfUtf16Units() {
        BrandMoodSaveReqDto request = validSaveRequest();
        request.setBrandSummary("😀".repeat(220));

        when(brandMoodRepository.findTopByBrandIdOrderByVersionDesc(1L))
                .thenReturn(Optional.empty());
        when(brandMoodRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BrandMoodRespDto response = brandMoodService.save(request, user);

        assertEquals("😀".repeat(220), response.getBrandSummary());
    }

    @Test
    void saveBuildsInstructionFromInternalAnalysisAndUserConfirmedValues() {
        BrandMoodSaveReqDto request = validSaveRequest();
        request.setBrandSummary("사용자가 수정한 최종 요약");
        request.setBrandInstruction("클라이언트가 임의로 만든 지시문");
        request.setRawAnalysisJson(nestedAnalysisJson("상세 내부 요약", "기존 화면 요약"));

        when(brandMoodRepository.findTopByBrandIdOrderByVersionDesc(1L))
                .thenReturn(Optional.empty());
        when(brandMoodRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BrandMoodRespDto response = brandMoodService.save(request, user);

        assertTrue(response.getBrandInstruction().contains("상세 내부 요약"));
        assertTrue(response.getBrandInstruction().contains("사용자가 수정한 최종 요약"));
        assertTrue(response.getBrandInstruction().contains("사용자 확인값을 우선"));
        assertFalse(response.getBrandInstruction().contains("클라이언트가 임의로 만든 지시문"));
    }

    private BrandMoodAnalyzeReqDto request() {
        when(brand.getName()).thenReturn("AVEN");
        when(brand.getSiteUrl()).thenReturn("https://aven.example.com");

        BrandMoodAnalyzeReqDto request = new BrandMoodAnalyzeReqDto();
        request.setSourceUrl("https://aven.example.com");
        request.setSourceText("알래스카 자연에서 영감을 받은 브랜드");
        return request;
    }

    private BrandMoodSaveReqDto validSaveRequest() {
        BrandMoodSaveReqDto request = new BrandMoodSaveReqDto();
        request.setSourceType("URL");
        request.setSourceUrl("https://aven.example.com");
        request.setBrandSummary("짧은 브랜드 요약");
        request.setDesignPhilosophy("절제된 기능성");
        request.setBrandTone(List.of("프리미엄", "차분함"));
        request.setColorTemperature("쿨톤");
        request.setSaturation("낮음");
        request.setContrast("중간");
        request.setLighting("부드러운 자연광");
        request.setSurfaceTexture("매트함");
        request.setBackgroundMood("자연 기반 에디토리얼");
        request.setComposition("깔끔한 중앙 구도");
        request.setPrimaryColors(List.of("#6EA1D4"));
        request.setAvoidElements(List.of("네온", "저가 쇼핑몰 느낌"));
        request.setCustomBrandNote("차갑지만 병원 같은 느낌은 피한다.");
        request.setRawAnalysisJson("{}");
        return request;
    }

    private String nestedAnalysisJson(String internalSummary, String displaySummary) {
        return """
                {
                  "internalAnalysis": %s,
                  "display": %s
                }
                """.formatted(valuesJson(internalSummary), valuesJson(displaySummary));
    }

    private String valuesJson(String summary) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.ofEntries(
                    Map.entry("brandSummary", summary),
                    Map.entry("designPhilosophy", "절제된 기능성"),
                    Map.entry("brandTone", List.of("프리미엄", "차분함")),
                    Map.entry("colorTemperature", "쿨톤"),
                    Map.entry("saturation", "낮음"),
                    Map.entry("contrast", "중간"),
                    Map.entry("lighting", "부드러운 자연광"),
                    Map.entry("surfaceTexture", "매트함"),
                    Map.entry("backgroundMood", "자연 기반 에디토리얼"),
                    Map.entry("composition", "깔끔한 중앙 구도"),
                    Map.entry("primaryColors", List.of("#6EA1D4")),
                    Map.entry("avoidElements", List.of("네온", "저가 쇼핑몰 느낌")),
                    Map.entry("customBrandNote", "차갑지만 병원 같은 느낌은 피한다.")
            ));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
