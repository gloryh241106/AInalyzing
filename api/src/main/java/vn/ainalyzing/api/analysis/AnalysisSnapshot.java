package vn.ainalyzing.api.analysis;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Snapshot Store entry (LLM Architecture §2): the immutable Java result of one analysis, owned by one HTTP session
 * and readable until expiresAt. The chat reads facts only from here. memo() caches derived results such as the
 * insight card so a second call returns the same content without calling Groq again (Spec A.3).
 */
public final class AnalysisSnapshot {

    private final String id;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final AnalysisResult result;
    private final Map<String, Object> memo = new HashMap<>();

    public AnalysisSnapshot(String id, Instant createdAt, Instant expiresAt, AnalysisResult result) {
        this.id = id;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.result = result;
    }

    public String id() {
        return id;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public AnalysisResult result() {
        return result;
    }

    public boolean expiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** Computes once per key; concurrent callers for the same snapshot wait for the first one. */
    @SuppressWarnings("unchecked")
    public synchronized <T> T memo(String key, Supplier<T> compute) {
        return (T) memo.computeIfAbsent(key, k -> compute.get());
    }
}
