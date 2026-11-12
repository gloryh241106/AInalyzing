package vn.ainalyzing.api;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.ainalyzing.api.llm.GroqProperties;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(GroqProperties.class)
public class AppConfig {

    /** Injected so snapshot expiry and Groq rate limits can be tested without waiting. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
