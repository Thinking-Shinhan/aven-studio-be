package kr.co.daeun.avenstudiobe.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 2 mapper for integrations that still use com.fasterxml.jackson types.
 * Spring Boot 4's web stack auto-configures Jackson 3 (tools.jackson) instead.
 */
@Configuration
public class LegacyJacksonConfig {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper legacyObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
