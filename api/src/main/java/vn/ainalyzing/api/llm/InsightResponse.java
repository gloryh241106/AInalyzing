package vn.ainalyzing.api.llm;

import vn.ainalyzing.api.analysis.Scenario;

import java.util.List;

/**
 * Spec A.3 insight card plus fallbackReason. Every text field was written by the Answer Renderer from Java values.
 *
 * @param model null when status is fallback
 */
public record InsightResponse(String analysisId, String status, FallbackReason fallbackReason, String model,
                              String promptVersion, Scenario suggestedScenario, Item scenarioReason,
                              List<Insight> insights, Recommendation recommendation, List<Item> supplierQuestions) {

    public record Item(String text, List<String> factIds) {
    }

    public record Insight(String kind, String text, List<String> factIds) {
    }

    public record Recommendation(String option, String text, List<String> factIds) {
    }
}
