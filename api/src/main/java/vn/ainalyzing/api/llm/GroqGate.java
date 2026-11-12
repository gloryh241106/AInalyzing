package vn.ainalyzing.api.llm;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Local request budget for the shared free-tier key: at most groq.max-calls-per-minute requests in any 60 s, and a
 * pause after Groq answers 429 (up to its Retry-After, capped at 60 s). A refused call becomes a template answer.
 */
@Component
public class GroqGate {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final int perMinute;
    private final Clock clock;
    private final Deque<Instant> calls = new ArrayDeque<>();
    private Instant pausedUntil = Instant.MIN;

    public GroqGate(GroqProperties props, Clock clock) {
        this.perMinute = Math.max(1, props.maxCallsPerMinute());
        this.clock = clock;
    }

    public synchronized boolean tryAcquire() {
        Instant now = clock.instant();
        if (now.isBefore(pausedUntil)) {
            return false;
        }
        while (!calls.isEmpty() && !calls.peekFirst().isAfter(now.minus(WINDOW))) {
            calls.pollFirst();
        }
        if (calls.size() >= perMinute) {
            return false;
        }
        calls.addLast(now);
        return true;
    }

    public synchronized void pause(Duration retryAfter) {
        Duration d = retryAfter == null ? Duration.ofSeconds(10) : retryAfter;
        if (d.compareTo(WINDOW) > 0) {
            d = WINDOW;
        }
        Instant until = clock.instant().plus(d);
        if (until.isAfter(pausedUntil)) {
            pausedUntil = until;
        }
    }
}
