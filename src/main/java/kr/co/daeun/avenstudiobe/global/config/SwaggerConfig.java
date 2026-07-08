package kr.co.daeun.avenstudiobe.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Aven Studio API",
                version = "v1",
                description = "Aven Studio 백엔드 API 문서입니다."
        )
)
@SecurityScheme(
        name = "accessTokenCookie",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE,
        paramName = "accessToken",
        description = "로그인 API에서 발급되는 HttpOnly accessToken 쿠키입니다."
)
public class SwaggerConfig {
}
