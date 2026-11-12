package vn.ainalyzing.api.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * groq.* settings (application.properties). The key and model live only on the server (AR-001, NFR-04).
 *
 * @param timeoutMs         deadline for one whole insight or chat turn, every Groq request included
 * @param maxCallsPerMinute local guard so a public demo cannot drain the shared key; beyond it the app uses templates
 * @param egressSources     demand sources whose aggregated facts may leave the VPS (csv, manual, demo)
 */
@ConfigurationProperties("groq")
public record GroqProperties(String apiKey,
                             @DefaultValue("openai/gpt-oss-20b") String model,
                             @DefaultValue("https://api.groq.com/openai/v1") String baseUrl,
                             @DefaultValue("5000") long timeoutMs,
                             @DefaultValue("20") int maxCallsPerMinute,
                             @DefaultValue({"demo", "manual", "csv"}) Set<String> egressSources) {

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    public Duration timeout() {
        return Duration.ofMillis(timeoutMs);
    }

    public boolean egressAllowed(String demandSource) {
        Set<String> allowed = egressSources.stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        return allowed.contains(demandSource);
    }

    /** Never print the key. */
    @Override
    public String toString() {
        return "GroqProperties[model=" + model + ", baseUrl=" + baseUrl + ", timeoutMs=" + timeoutMs
                + ", maxCallsPerMinute=" + maxCallsPerMinute + ", egressSources=" + egressSources
                + ", apiKey=" + (enabled() ? "***" : "(none)") + "]";
    }
}
