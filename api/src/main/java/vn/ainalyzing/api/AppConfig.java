package vn.ainalyzing.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppConfig {

    /** Injected so snapshot expiry and Groq rate limits can be tested without waiting. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
