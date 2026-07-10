package kr.co.daeun.avenstudiobe.global.ai.claude;

public class ClaudeApiException extends RuntimeException {

    public ClaudeApiException(String message) {
        super(message);
    }

    public ClaudeApiException(String message, Throwable cause) {
        super(message, cause);
    }

    public ClaudeApiException(int statusCode, String responseBody) {
        super("Claude API request failed. status=" + statusCode + ", response=" + abbreviate(responseBody));
    }

    private static String abbreviate(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        int maxLength = 1000;
        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength) + "...";
    }
}
