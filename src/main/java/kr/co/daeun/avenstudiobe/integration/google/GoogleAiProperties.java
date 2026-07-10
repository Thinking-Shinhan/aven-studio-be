package kr.co.daeun.avenstudiobe.integration.google;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "google.ai")
public class GoogleAiProperties {

    private String apiKey;
    private String imageModel = "gemini-2.5-flash-image";
    private Integer timeoutSeconds = 120;
}
