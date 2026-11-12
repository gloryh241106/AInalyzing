package vn.ainalyzing.api.llm;

/** Why an answer came from the local templates instead of Groq (returned as fallbackReason, logged as a code). */
public enum FallbackReason {
    /** No GROQ_API_KEY on the server. */
    DISABLED,
    /** The demand source is not in groq.egress-sources, so its facts stay on the VPS. */
    LOCAL_POLICY,
    /** Local per-minute guard or HTTP 429 from Groq. */
    RATE_LIMITED,
    /** The turn's deadline passed. */
    TIMEOUT,
    /** Network error or HTTP 5xx. */
    UPSTREAM_ERROR,
    /** Groq refused the request (HTTP 4xx other than 429). */
    REJECTED,
    /** The reply was not valid JSON, broke the schema, or cited templates/facts that do not fit this analysis. */
    INVALID_OUTPUT,
    /** The model asked for a tool or arguments that are not allowed. */
    TOOL_FAILURE
}
