package kr.co.daeun.avenstudiobe.global.config;

import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfig {

    private static final String MAX_PART_HEADER_SIZE = "8192";

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> factory.addConnectorCustomizers(connector ->
                connector.setProperty("maxPartHeaderSize", MAX_PART_HEADER_SIZE));
    }
}
