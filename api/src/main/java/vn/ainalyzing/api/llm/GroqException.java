package vn.ainalyzing.api.llm;

import java.time.Duration;

/** A Groq call that did not produce a usable reply. Carries no request or response content. */
public class GroqException extends RuntimeException {

    private final FallbackReason reason;
    private final Duration retryAfter;

    public GroqException(FallbackReason reason, String detail) {
        this(reason, detail, null);
    }

    public GroqException(FallbackReason reason, String detail, Duration retryAfter) {
        super(reason + ": " + detail);
        this.reason = reason;
        this.retryAfter = retryAfter;
    }

    public FallbackReason reason() {
        return reason;
    }

    /** Only for RATE_LIMITED replies that said how long to wait. */
    public Duration retryAfter() {
        return retryAfter;
    }
}
