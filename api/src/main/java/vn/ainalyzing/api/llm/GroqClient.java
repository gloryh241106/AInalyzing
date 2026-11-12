package vn.ainalyzing.api.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Groq Adapter (LLM Architecture §2): one HTTPS POST to the OpenAI-compatible Chat Completions endpoint. Uses the JDK
 * HttpClient because each request needs its own timeout (the remaining part of the turn's 5 s deadline). Logs the
 * HTTP status, duration and Groq request id only; never the prompt, facts, question or key.
 */
@Component
public class GroqClient {

    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);

    /** Assistant reply: either JSON text in content, or tool calls. message is kept verbatim to echo it back. */
    public record Completion(String content, List<ToolCall> toolCalls, String finishReason, JsonNode message) {
    }

    public record ToolCall(String id, String name, String arguments) {
    }

    private final GroqProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public GroqClient(GroqProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public Completion complete(ObjectNode body, Duration timeout, String kind) {
        if (timeout.toMillis() < 200) {
            throw new GroqException(FallbackReason.TIMEOUT, "no time left");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(props.baseUrl() + "/chat/completions"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + props.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        long start = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            logCall(kind, "timeout", start, null);
            throw new GroqException(FallbackReason.TIMEOUT, "request timed out");
        } catch (IOException e) {
            logCall(kind, "io:" + e.getClass().getSimpleName(), start, null);
            throw new GroqException(FallbackReason.UPSTREAM_ERROR, "network error");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GroqException(FallbackReason.TIMEOUT, "interrupted");
        }

        int status = response.statusCode();
        String groqId = response.headers().firstValue("x-request-id").orElse(null);
        logCall(kind, String.valueOf(status), start, groqId);
        if (status == 429) {
            throw new GroqException(FallbackReason.RATE_LIMITED, "429", retryAfter(response));
        }
        if (status >= 500) {
            throw new GroqException(FallbackReason.UPSTREAM_ERROR, "http " + status);
        }
        if (status != 200) {
            // 400 here usually means the model could not satisfy the strict schema (json_validate_failed).
            log.warn("groq kind={} rejected http={} code={}", kind, status, errorCode(response.body()));
            throw new GroqException(status == 400 ? FallbackReason.INVALID_OUTPUT : FallbackReason.REJECTED,
                    "http " + status);
        }
        return parse(response.body(), kind);
    }

    private Completion parse(String body, String kind) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode usage = root.path("usage");
            log.info("groq kind={} usage prompt={} completion={} total={}", kind, usage.path("prompt_tokens").asInt(-1),
                    usage.path("completion_tokens").asInt(-1), usage.path("total_tokens").asInt(-1));
            JsonNode message = root.path("choices").path(0).path("message");
            String finish = root.path("choices").path(0).path("finish_reason").asString(null);
            if (message.isMissingNode()) {
                throw new GroqException(FallbackReason.INVALID_OUTPUT, "no choices");
            }
            List<ToolCall> calls = new ArrayList<>();
            for (JsonNode c : message.path("tool_calls")) {
                calls.add(new ToolCall(c.path("id").asString(null), c.path("function").path("name").asString(null),
                        c.path("function").path("arguments").asString(null)));
            }
            JsonNode content = message.get("content");
            return new Completion(content == null || content.isNull() ? null : content.asString(), calls, finish, message);
        } catch (JacksonException e) {
            throw new GroqException(FallbackReason.INVALID_OUTPUT, "unreadable body");
        }
    }

    private String errorCode(String body) {
        try {
            return mapper.readTree(body).path("error").path("code").asString("?");
        } catch (JacksonException e) {
            return "?";
        }
    }

    private static Duration retryAfter(HttpResponse<?> response) {
        return response.headers().firstValue("retry-after").map(v -> {
            try {
                return Duration.ofMillis((long) (Double.parseDouble(v) * 1000));
            } catch (NumberFormatException e) {
                return null;
            }
        }).orElse(null);
    }

    private void logCall(String kind, String outcome, long startNanos, String groqId) {
        log.info("groq kind={} model={} outcome={} ms={} groqRequestId={}", kind, props.model(), outcome,
                (System.nanoTime() - startNanos) / 1_000_000, groqId);
    }
}
