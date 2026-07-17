package kr.co.daeun.avenstudiobe.domain.brandmood.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.daeun.avenstudiobe.domain.brand.entity.Brand;
import kr.co.daeun.avenstudiobe.domain.brandmood.dto.*;
import kr.co.daeun.avenstudiobe.domain.brandmood.entity.BrandMood;
import kr.co.daeun.avenstudiobe.domain.brandmood.repository.BrandMoodRepository;
import kr.co.daeun.avenstudiobe.domain.user.entity.User;
import kr.co.daeun.avenstudiobe.integration.anthropic.AnthropicMoodClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandMoodService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final long MAX_PDF_SIZE_BYTES = 20L * 1024 * 1024;
    private static final int BRAND_SUMMARY_MAX_LENGTH = 220;
    private static final int DESIGN_PHILOSOPHY_MAX_LENGTH = 84;
    private static final int BRAND_TONE_MAX_COUNT = 8;
    private static final int BRAND_TONE_MAX_LENGTH = 84;
    private static final int MOOD_ATTRIBUTE_MAX_LENGTH = 25;
    private static final int AVOID_ELEMENTS_MAX_COUNT = 8;
    private static final int AVOID_ELEMENTS_MAX_LENGTH = 100;
    private static final int CUSTOM_BRAND_NOTE_MAX_LENGTH = 120;

    private final BrandMoodRepository brandMoodRepository;
    private final AnthropicMoodClient anthropicMoodClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BrandMoodAnalysisRespDto analyze(BrandMoodAnalyzeReqDto request, MultipartFile file, User user) {
        Brand brand = user.getBrand();
        validateRequiredSourceUrl(request);
        boolean pdfAttached = hasPdf(file);
        request.setSourceType(pdfAttached ? "PDF" : "URL");

        String systemPrompt = buildAnalyzeSystemPrompt();
        String userPrompt = buildAnalyzeUserPrompt(brand, request);

        if (pdfAttached) {
            validatePdf(file);
            String claudeText = anthropicMoodClient.analyzeBrandMoodWithPdf(systemPrompt, userPrompt, readPdf(file));
            return parseAnalysisResponse(request, claudeText, "PDF", brand.getName());
        }

        String claudeText = anthropicMoodClient.analyzeBrandMood(systemPrompt, userPrompt);
        return parseAnalysisResponse(request, claudeText, "URL", brand.getName());
    }

    private boolean hasPdf(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    private BrandMoodAnalysisRespDto parseAnalysisResponse(
            BrandMoodAnalyzeReqDto request,
            String claudeText,
            String defaultSourceType,
            String brandName
    ) {
        String jsonText = extractJsonObject(claudeText);
        try {
            BrandMoodAnalysisPayloadDto payload = objectMapper.readValue(jsonText, BrandMoodAnalysisPayloadDto.class);
            validateAnalysisPayload(payload);

            List<String> violations = findDisplayViolations(payload.getDisplay());
            if (!violations.isEmpty()) {
                payload.setDisplay(correctDisplayValues(payload.getDisplay(), violations));
                List<String> correctedViolations = findDisplayViolations(payload.getDisplay());
                if (!correctedViolations.isEmpty()) {
                    throw new IllegalStateException(
                            "브랜드 무드 표시값이 길이 제한을 충족하지 않습니다: "
                                    + String.join(", ", correctedViolations)
                    );
                }
            }

            payload.setSourceType(defaultSourceType);
            BrandMoodAnalysisValuesDto display = payload.getDisplay();
            return new BrandMoodAnalysisRespDto(
                    normalizeSourceUrl(request.getSourceUrl()),
                    brandName,
                    display.getBrandSummary(),
                    display.getDesignPhilosophy(),
                    safeList(display.getBrandTone()),
                    display.getColorTemperature(),
                    display.getSaturation(),
                    display.getContrast(),
                    display.getLighting(),
                    display.getSurfaceTexture(),
                    display.getBackgroundMood(),
                    display.getComposition(),
                    safeList(display.getPrimaryColors()),
                    safeList(display.getAvoidElements()),
                    display.getCustomBrandNote(),
                    objectMapper.writeValueAsString(payload)
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("브랜드 무드 분석 결과 JSON 파싱에 실패했습니다.", e);
        }
    }

    @Transactional
    public BrandMoodRespDto save(BrandMoodSaveReqDto request, User user) {
        Brand brand = user.getBrand();
        Long brandId = brand.getId();
        validateDisplayValues(toAnalysisValues(request));

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
                resolveSourceType(request),
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
    public BrandMoodRespDto getCurrent(User user) {
        Long brandId = user.getBrand().getId();

        BrandMood brandMood = brandMoodRepository.findTopByBrandIdAndActiveTrueOrderByVersionDesc(brandId)
                .orElseThrow(() -> new IllegalArgumentException("활성화된 브랜드 무드가 없습니다."));

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

    private void validateRequiredSourceUrl(BrandMoodAnalyzeReqDto request) {
        if (request.getSourceUrl() == null || request.getSourceUrl().isBlank()) {
            throw new IllegalArgumentException("브랜드 무드 분석을 위한 sourceUrl을 입력해주세요.");
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
        BrandMoodAnalysisValuesDto internalAnalysis = readInternalAnalysis(request.getRawAnalysisJson());

        return """
                [INTERNAL BRAND ANALYSIS]
                아래 내용은 이미지 생성 품질을 위한 상세 분석이다.
                사용자 확인값과 충돌하면 반드시 사용자 확인값을 우선한다.
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

                [USER CONFIRMED BRAND MOOD]
                이 섹션은 사용자가 확인하거나 수정한 최종 기준이며 위 상세 분석보다 우선한다.
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
                nullToEmpty(internalAnalysis.getBrandSummary()),
                nullToEmpty(internalAnalysis.getDesignPhilosophy()),
                safeList(internalAnalysis.getBrandTone()),
                nullToEmpty(internalAnalysis.getColorTemperature()),
                nullToEmpty(internalAnalysis.getSaturation()),
                nullToEmpty(internalAnalysis.getContrast()),
                nullToEmpty(internalAnalysis.getLighting()),
                nullToEmpty(internalAnalysis.getSurfaceTexture()),
                nullToEmpty(internalAnalysis.getBackgroundMood()),
                nullToEmpty(internalAnalysis.getComposition()),
                safeList(internalAnalysis.getAvoidElements()),
                nullToEmpty(internalAnalysis.getCustomBrandNote()),
                nullToEmpty(request.getBrandSummary()),
                nullToEmpty(request.getDesignPhilosophy()),
                safeList(request.getBrandTone()),
                nullToEmpty(moodAttributes.getColorTemperature()),
                nullToEmpty(moodAttributes.getSaturation()),
                nullToEmpty(moodAttributes.getContrast()),
                nullToEmpty(moodAttributes.getLighting()),
                nullToEmpty(moodAttributes.getSurfaceTexture()),
                nullToEmpty(moodAttributes.getBackgroundMood()),
                nullToEmpty(moodAttributes.getComposition()),
                safeList(request.getAvoidElements()),
                nullToEmpty(request.getCustomBrandNote())
        );
    }

    private String buildAnalyzeSystemPrompt() {
        return """
                당신은 패션 이커머스 브랜드 무드 분석가다.
                사용자가 제공한 자료 안에서 이미지 생성에 필요한 브랜드 시각 무드를 추출한다.
                제공된 자료 안에서 근거를 최대한 적극적으로 찾되, 자료로 판단할 수 없는 값은 null 또는 빈 배열로 둔다.
                외부 웹사이트를 직접 탐색했다고 가정하지 않는다.
                숨겨진 사고 과정이나 단계별 추론을 출력하지 않는다.
                반드시 JSON 객체만 출력한다. 설명, 마크다운, 코드블록을 출력하지 않는다.

                internalAnalysis에는 이미지 생성 프롬프트에 활용할 수 있도록 충분히 구체적인 분석을 작성한다.
                display에는 internalAnalysis의 의미를 보존하면서 사용자가 빠르게 확인할 수 있는 짧은 표현을 작성한다.

                display 작성 제한:
                - brandSummary: 최대 220자
                - designPhilosophy: 최대 84자
                - brandTone: 최대 8개, 쉼표와 공백으로 연결했을 때 최대 84자
                - colorTemperature, saturation, contrast, lighting, surfaceTexture, backgroundMood, composition: 각각 최대 25자
                - avoidElements: 최대 8개, 쉼표와 공백으로 연결했을 때 최대 100자
                - customBrandNote: 최대 120자
                - 확인되지 않은 값은 억지로 채우지 말고 null 또는 빈 배열로 작성한다.

                출력 JSON 스키마:
                {
                  "internalAnalysis": {
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
                  },
                  "display": {
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
                }
                """;
    }

    private BrandMoodAnalysisValuesDto correctDisplayValues(
            BrandMoodAnalysisValuesDto display,
            List<String> violations
    ) {
        String systemPrompt = """
                당신은 JSON 편집기다.
                입력된 display JSON에서 길이 제한을 위반한 필드만 의미를 유지하며 간결하게 고친다.
                제한을 지키는 필드는 값과 순서를 그대로 유지한다.
                새로운 사실을 추가하거나 internalAnalysis를 다시 분석하지 않는다.
                반드시 수정된 display JSON 객체만 출력한다.

                제한:
                - brandSummary: 최대 220자
                - designPhilosophy: 최대 84자
                - brandTone: 최대 8개, 쉼표와 공백으로 연결했을 때 최대 84자
                - colorTemperature, saturation, contrast, lighting, surfaceTexture, backgroundMood, composition: 각각 최대 25자
                - avoidElements: 최대 8개, 쉼표와 공백으로 연결했을 때 최대 100자
                - customBrandNote: 최대 120자
                """;

        try {
            String userPrompt = """
                    [제한 위반 필드]
                    %s

                    [display JSON]
                    %s
                    """.formatted(
                    String.join(", ", violations),
                    objectMapper.writeValueAsString(display)
            );
            String correctedText = anthropicMoodClient.analyzeBrandMood(systemPrompt, userPrompt);
            return objectMapper.readValue(
                    extractJsonObject(correctedText),
                    BrandMoodAnalysisValuesDto.class
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("브랜드 무드 표시값 교정 결과 JSON 파싱에 실패했습니다.", e);
        }
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

    private void validateAnalysisPayload(BrandMoodAnalysisPayloadDto payload) {
        if (payload == null || payload.getInternalAnalysis() == null || payload.getDisplay() == null) {
            throw new IllegalStateException(
                    "브랜드 무드 분석 결과에 internalAnalysis 또는 display가 없습니다."
            );
        }
    }

    private void validateDisplayValues(BrandMoodAnalysisValuesDto display) {
        List<String> violations = findDisplayViolations(display);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(
                    "브랜드 무드 표시값 제한을 확인해주세요: " + String.join(", ", violations)
            );
        }
    }

    private List<String> findDisplayViolations(BrandMoodAnalysisValuesDto display) {
        if (display == null) {
            return List.of("display");
        }

        List<String> violations = new ArrayList<>();
        addLengthViolation(violations, "brandSummary", display.getBrandSummary(), BRAND_SUMMARY_MAX_LENGTH);
        addLengthViolation(
                violations,
                "designPhilosophy",
                display.getDesignPhilosophy(),
                DESIGN_PHILOSOPHY_MAX_LENGTH
        );
        addListViolation(
                violations,
                "brandTone",
                display.getBrandTone(),
                BRAND_TONE_MAX_COUNT,
                BRAND_TONE_MAX_LENGTH
        );
        addLengthViolation(
                violations,
                "colorTemperature",
                display.getColorTemperature(),
                MOOD_ATTRIBUTE_MAX_LENGTH
        );
        addLengthViolation(violations, "saturation", display.getSaturation(), MOOD_ATTRIBUTE_MAX_LENGTH);
        addLengthViolation(violations, "contrast", display.getContrast(), MOOD_ATTRIBUTE_MAX_LENGTH);
        addLengthViolation(violations, "lighting", display.getLighting(), MOOD_ATTRIBUTE_MAX_LENGTH);
        addLengthViolation(
                violations,
                "surfaceTexture",
                display.getSurfaceTexture(),
                MOOD_ATTRIBUTE_MAX_LENGTH
        );
        addLengthViolation(
                violations,
                "backgroundMood",
                display.getBackgroundMood(),
                MOOD_ATTRIBUTE_MAX_LENGTH
        );
        addLengthViolation(violations, "composition", display.getComposition(), MOOD_ATTRIBUTE_MAX_LENGTH);
        addListViolation(
                violations,
                "avoidElements",
                display.getAvoidElements(),
                AVOID_ELEMENTS_MAX_COUNT,
                AVOID_ELEMENTS_MAX_LENGTH
        );
        addLengthViolation(
                violations,
                "customBrandNote",
                display.getCustomBrandNote(),
                CUSTOM_BRAND_NOTE_MAX_LENGTH
        );
        return violations;
    }

    private void addLengthViolation(
            List<String> violations,
            String fieldName,
            String value,
            int maxLength
    ) {
        if (codePointLength(value) > maxLength) {
            violations.add(fieldName + "(최대 " + maxLength + "자)");
        }
    }

    private void addListViolation(
            List<String> violations,
            String fieldName,
            List<String> values,
            int maxCount,
            int maxJoinedLength
    ) {
        List<String> safeValues = safeList(values);
        if (safeValues.size() > maxCount) {
            violations.add(fieldName + "(최대 " + maxCount + "개)");
        }
        if (codePointLength(joinValues(safeValues)) > maxJoinedLength) {
            violations.add(fieldName + "(합산 최대 " + maxJoinedLength + "자)");
        }
    }

    private String joinValues(List<String> values) {
        return safeList(values).stream()
                .map(this::nullToEmpty)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private int codePointLength(String value) {
        if (value == null) {
            return 0;
        }
        return value.codePointCount(0, value.length());
    }

    private BrandMoodAnalysisValuesDto toAnalysisValues(BrandMoodSaveReqDto request) {
        return new BrandMoodAnalysisValuesDto(
                request.getBrandSummary(),
                request.getDesignPhilosophy(),
                safeList(request.getBrandTone()),
                request.getColorTemperature(),
                request.getSaturation(),
                request.getContrast(),
                request.getLighting(),
                request.getSurfaceTexture(),
                request.getBackgroundMood(),
                request.getComposition(),
                safeList(request.getPrimaryColors()),
                safeList(request.getAvoidElements()),
                request.getCustomBrandNote()
        );
    }

    private BrandMoodAnalysisValuesDto readInternalAnalysis(String rawAnalysisJson) {
        if (rawAnalysisJson == null || rawAnalysisJson.isBlank()) {
            return new BrandMoodAnalysisValuesDto();
        }

        try {
            JsonNode root = objectMapper.readTree(rawAnalysisJson);
            JsonNode internalNode = root.path("internalAnalysis");
            if (!internalNode.isMissingNode() && !internalNode.isNull()) {
                return objectMapper.treeToValue(internalNode, BrandMoodAnalysisValuesDto.class);
            }

            // 기존 평면 rawAnalysisJson으로 저장 요청을 보내는 클라이언트도 계속 지원한다.
            return objectMapper.treeToValue(root, BrandMoodAnalysisValuesDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("rawAnalysisJson 형식이 올바르지 않습니다.");
        }
    }

    private String resolveSourceType(BrandMoodSaveReqDto request) {
        if (request.getSourceType() != null && !request.getSourceType().isBlank()) {
            return request.getSourceType();
        }

        String rawAnalysisJson = request.getRawAnalysisJson();
        if (rawAnalysisJson != null && !rawAnalysisJson.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(rawAnalysisJson);
                String sourceType = root.path("sourceType").asText(null);
                if (sourceType != null && !sourceType.isBlank()) {
                    return sourceType;
                }
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("rawAnalysisJson 형식이 올바르지 않습니다.");
            }
        }

        return request.getSourceUrl() == null || request.getSourceUrl().isBlank() ? "TEXT" : "URL";
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
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
