package kr.co.daeun.avenstudiobe.domain.brandmood.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.daeun.avenstudiobe.domain.brand.entity.Brand;
import kr.co.daeun.avenstudiobe.domain.brand.repository.BrandRepository;
import kr.co.daeun.avenstudiobe.domain.brandmood.dto.*;
import kr.co.daeun.avenstudiobe.domain.brandmood.entity.BrandMood;
import kr.co.daeun.avenstudiobe.domain.brandmood.repository.BrandMoodRepository;
import kr.co.daeun.avenstudiobe.domain.user.entity.User;
import kr.co.daeun.avenstudiobe.global.exception.BrandNotFoundException;
import kr.co.daeun.avenstudiobe.integration.anthropic.AnthropicMoodClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandMoodService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final long MAX_PDF_SIZE_BYTES = 20L * 1024 * 1024;

    private final BrandMoodRepository brandMoodRepository;
    private final BrandRepository brandRepository;
    private final AnthropicMoodClient anthropicMoodClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BrandMoodAnalysisRespDto analyze(Long brandId, BrandMoodAnalyzeReqDto request, User user) {
        Brand brand = findBrand(brandId);
        validateBrandOwner(user, brandId);

        String systemPrompt = buildAnalyzeSystemPrompt();
        String userPrompt = buildAnalyzeUserPrompt(brand, request);
        String claudeText = anthropicMoodClient.analyzeBrandMood(systemPrompt, userPrompt);
        return parseAnalysisResponse(request, claudeText, "TEXT");
    }

    public BrandMoodAnalysisRespDto analyzePdf(Long brandId, BrandMoodAnalyzeReqDto request, MultipartFile file, User user) {
        Brand brand = findBrand(brandId);
        validateBrandOwner(user, brandId);
        validatePdf(file);

        String systemPrompt = buildAnalyzeSystemPrompt();
        String userPrompt = buildAnalyzeUserPrompt(brand, request);
        String claudeText = anthropicMoodClient.analyzeBrandMoodWithPdf(systemPrompt, userPrompt, readPdf(file));
        return parseAnalysisResponse(request, claudeText, "PDF");
    }

    public BrandMoodAnalysisRespDto analyzePdf(Long brandId, String requestJson, MultipartFile file, User user) {
        return analyzePdf(brandId, parseAnalyzeRequest(requestJson), file, user);
    }

    private BrandMoodAnalyzeReqDto parseAnalyzeRequest(String requestJson) {
        if (requestJson == null || requestJson.isBlank()) {
            throw new IllegalArgumentException("request JSON을 입력해주세요.");
        }

        try {
            return objectMapper.readValue(requestJson, BrandMoodAnalyzeReqDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("request JSON 형식이 올바르지 않습니다.", e);
        }
    }

    private BrandMoodAnalysisRespDto parseAnalysisResponse(
            BrandMoodAnalyzeReqDto request,
            String claudeText,
            String defaultSourceType
    ) {
        String jsonText = extractJsonObject(claudeText);
        try {
            BrandMoodAnalysisRespDto analysis = objectMapper.readValue(jsonText, BrandMoodAnalysisRespDto.class);
            return new BrandMoodAnalysisRespDto(
                    defaultIfBlank(request.getSourceType(), defaultSourceType),
                    normalizeSourceUrl(request.getSourceUrl()),
                    analysis.getBrandSummary(),
                    analysis.getDesignPhilosophy(),
                    analysis.getBrandTone(),
                    analysis.getColorTemperature(),
                    analysis.getSaturation(),
                    analysis.getContrast(),
                    analysis.getLighting(),
                    analysis.getSurfaceTexture(),
                    analysis.getBackgroundMood(),
                    analysis.getComposition(),
                    analysis.getPrimaryColors(),
                    analysis.getAvoidElements(),
                    analysis.getCustomBrandNote(),
                    jsonText
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("브랜드 무드 분석 결과 JSON 파싱에 실패했습니다.", e);
        }
    }

    @Transactional
    public BrandMoodRespDto save(Long brandId, BrandMoodSaveReqDto request, User user) {
        Brand brand = findBrand(brandId);
        validateBrandOwner(user, brandId);

        int version = brandMoodRepository.findTopByBrandIdOrderByVersionDesc(brandId)
                .map(brandMood -> brandMood.getVersion() + 1)
                .orElse(1);

        MoodAttributesRespDto moodAttributes = toMoodAttributes(request);
        String brandInstruction = resolveBrandInstruction(request, moodAttributes);

        BrandMood brandMood = BrandMood.create(
                user,
                brand,
                defaultIfBlank(request.getName(), brand.getName() + " 기본 무드"),
                version,
                defaultIfBlank(request.getSourceType(), "TEXT"),
                normalizeSourceUrl(request.getSourceUrl()),
                request.getBrandSummary(),
                request.getDesignPhilosophy(),
                toJson(request.getBrandTone()),
                toJson(moodAttributes),
                toJson(request.getPrimaryColors()),
                toJson(request.getAvoidElements()),
                brandInstruction,
                request.getCustomBrandNote(),
                normalizeJson(request.getRawAnalysisJson())
        );

        return toResponse(brandMoodRepository.save(brandMood));
    }

    @Transactional(readOnly = true)
    public BrandMoodRespDto get(Long brandId, Long brandMoodId, User user) {
        validateBrandOwner(user, brandId);

        BrandMood brandMood = brandMoodRepository.findById(brandMoodId)
                .orElseThrow(() -> new IllegalArgumentException("브랜드 무드를 찾을 수 없습니다."));

        if (!brandMood.getBrand().getId().equals(brandId)) {
            throw new IllegalArgumentException("브랜드에 속한 무드가 아닙니다.");
        }

        return toResponse(brandMood);
    }

    private BrandMoodRespDto toResponse(BrandMood brandMood) {
        return BrandMoodRespDto.from(
                brandMood,
                fromJson(brandMood.getToneKeywords(), STRING_LIST_TYPE),
                fromJson(brandMood.getMoodAttributes(), MoodAttributesRespDto.class),
                fromJson(brandMood.getColorPalette(), STRING_LIST_TYPE),
                fromJson(brandMood.getNegativeKeywords(), STRING_LIST_TYPE)
        );
    }

    private Brand findBrand(Long brandId) {
        return brandRepository.findById(brandId)
                .orElseThrow(BrandNotFoundException::new);
    }

    private void validateBrandOwner(User user, Long brandId) {
        if (!user.getBrand().getId().equals(brandId)) {
            throw new IllegalArgumentException("로그인 사용자의 브랜드와 요청 브랜드가 일치하지 않습니다.");
        }
    }

    private MoodAttributesRespDto toMoodAttributes(BrandMoodSaveReqDto request) {
        return new MoodAttributesRespDto(
                request.getColorTemperature(),
                request.getSaturation(),
                request.getContrast(),
                request.getLighting(),
                request.getSurfaceTexture(),
                request.getBackgroundMood(),
                request.getComposition()
        );
    }

    private String resolveBrandInstruction(BrandMoodSaveReqDto request, MoodAttributesRespDto moodAttributes) {
        if (request.getBrandInstruction() != null && !request.getBrandInstruction().isBlank()) {
            return request.getBrandInstruction();
        }

        return """
                [BRAND MOOD]
                브랜드 요약: %s
                디자인 철학: %s
                브랜드 톤: %s
                색온도: %s
                채도: %s
                대비: %s
                조명: %s
                질감: %s
                배경 무드: %s
                구도: %s
                피해야 할 요소: %s
                추가 메모: %s
                """.formatted(
                nullToEmpty(request.getBrandSummary()),
                nullToEmpty(request.getDesignPhilosophy()),
                request.getBrandTone(),
                nullToEmpty(moodAttributes.getColorTemperature()),
                nullToEmpty(moodAttributes.getSaturation()),
                nullToEmpty(moodAttributes.getContrast()),
                nullToEmpty(moodAttributes.getLighting()),
                nullToEmpty(moodAttributes.getSurfaceTexture()),
                nullToEmpty(moodAttributes.getBackgroundMood()),
                nullToEmpty(moodAttributes.getComposition()),
                request.getAvoidElements(),
                nullToEmpty(request.getCustomBrandNote())
        );
    }

    private String buildAnalyzeSystemPrompt() {
        return """
                당신은 패션 이커머스 브랜드 무드 분석가다.
                사용자가 제공한 자료 안에서 이미지 생성에 필요한 브랜드 시각 무드를 추출한다.
                제공된 자료 안에서 근거를 최대한 적극적으로 찾되, 자료로 판단할 수 없는 값은 null 또는 빈 배열로 둔다.
                외부 웹사이트를 직접 탐색했다고 가정하지 않는다.
                반드시 JSON 객체만 출력한다. 설명, 마크다운, 코드블록을 출력하지 않는다.

                출력 JSON 스키마:
                {
                  "brandSummary": "string|null",
                  "designPhilosophy": "string|null",
                  "brandTone": ["string"],
                  "colorTemperature": "웜톤|뉴트럴|쿨톤|null",
                  "saturation": "낮음|중간|높음|null",
                  "contrast": "낮음|중간|높음|null",
                  "lighting": "string|null",
                  "surfaceTexture": "string|null",
                  "backgroundMood": "string|null",
                  "composition": "string|null",
                  "primaryColors": ["string"],
                  "avoidElements": ["string"],
                  "customBrandNote": "string|null"
                }
                """;
    }

    private String buildAnalyzeUserPrompt(Brand brand, BrandMoodAnalyzeReqDto request) {
        String sourceText = request.getSourceText();
        if (sourceText == null || sourceText.isBlank()) {
            sourceText = "제공된 본문 없음. 첨부 PDF, sourceUrl, 브랜드명만 참고하고, 확실하지 않은 항목은 null 또는 빈 배열로 둔다.";
        }

        return """
                [브랜드 정보]
                brandId: %d
                brandName: %s
                registeredSiteUrl: %s

                [입력 자료]
                sourceType: %s
                sourceUrl: %s
                sourceText:
                %s

                [추가 지시]
                %s
                """.formatted(
                brand.getId(),
                brand.getName(),
                nullToEmpty(brand.getSiteUrl()),
                nullToEmpty(request.getSourceType()),
                nullToEmpty(normalizeSourceUrl(request.getSourceUrl())),
                sourceText,
                nullToEmpty(request.getCustomInstruction())
        );
    }

    private void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("분석할 PDF 파일을 첨부해주세요.");
        }

        if (file.getSize() > MAX_PDF_SIZE_BYTES) {
            throw new IllegalArgumentException("PDF 파일은 20MB 이하만 첨부할 수 있습니다.");
        }

        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        boolean pdfContentType = "application/pdf".equalsIgnoreCase(contentType);
        boolean pdfExtension = filename != null && filename.toLowerCase().endsWith(".pdf");

        if (!pdfContentType && !pdfExtension) {
            throw new IllegalArgumentException("PDF 파일만 첨부할 수 있습니다.");
        }
    }

    private byte[] readPdf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("PDF 파일을 읽는 중 오류가 발생했습니다.", e);
        }
    }

    private String extractJsonObject(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("브랜드 무드 분석 결과가 비어 있습니다.");
        }

        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```json\\s*", "")
                    .replaceFirst("^```\\s*", "")
                    .replaceFirst("\\s*```$", "")
                    .trim();
        }

        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalStateException("브랜드 무드 분석 결과에서 JSON 객체를 찾을 수 없습니다.");
        }

        return trimmed.substring(start, end + 1);
    }

    private String normalizeJson(String json) {
        if (json == null || json.isBlank()) {
            return "{}";
        }

        try {
            JsonNode node = objectMapper.readTree(json);
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("rawAnalysisJson 형식이 올바르지 않습니다.");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("브랜드 무드 JSON 변환에 실패했습니다.", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("브랜드 무드 JSON 파싱에 실패했습니다.", e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("브랜드 무드 JSON 파싱에 실패했습니다.", e);
        }
    }

    private String defaultIfBlank(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value;
    }

    private String normalizeSourceUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return null;
        }

        String normalized = sourceUrl.trim();
        if (normalized.startsWith("<") && normalized.endsWith(">") && normalized.length() > 2) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }

        return normalized;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
