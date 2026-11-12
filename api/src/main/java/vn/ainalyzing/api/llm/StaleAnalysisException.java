package vn.ainalyzing.api.llm;

/** Scope Gate: the snapshot expired or the session was reset while a turn was running. Answered 404 stale_analysis. */
public class StaleAnalysisException extends RuntimeException {

    public StaleAnalysisException() {
        super("stale_analysis");
    }
}
