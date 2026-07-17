package kr.co.daeun.avenstudiobe.domain.sheet.mapper;

import java.util.Map;

public final class ProductAttributeCodes {

    public static final Map<String, Integer> LINING = Map.of(
            "기모", 1,
            "있음", 2,
            "없음", 3
    );

    public static final Map<String, Integer> ELASTICITY = Map.of(
            "매우좋음", 1,
            "보통", 2,
            "없음", 3
    );

    public static final Map<String, Integer> TRANSPARENCY = Map.of(
            "있음", 1,
            "약간", 2,
            "없음", 3
    );

    public static final Map<String, Integer> TOUCH_FEELING = Map.of(
            "쾌적함", 1,
            "부드러움", 2,
            "드라이함", 3
    );

    public static final Map<String, Integer> THICKNESS = Map.of(
            "두꺼움", 1,
            "보통", 2,
            "얇음", 3
    );

    public static String toDisplayText(AttributeType attributeType, int code) {
        if (attributeType == null) {
            return null;
        }

        Map<String, Integer> codes = switch (attributeType) {
            case LINING -> LINING;
            case ELASTICITY -> ELASTICITY;
            case TRANSPARENCY -> TRANSPARENCY;
            case TOUCH_FEELING -> TOUCH_FEELING;
            case THICKNESS -> THICKNESS;
        };

        return codes.entrySet().stream()
                .filter(entry -> entry.getValue() == code)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    public enum AttributeType {
        LINING,
        ELASTICITY,
        TRANSPARENCY,
        TOUCH_FEELING,
        THICKNESS
    }

    private ProductAttributeCodes() {}
}
