package vn.ainalyzing.api.analysis;

import vn.ainalyzing.api.csv.SalesSummaryBuilder.SalesSummary;

import java.util.List;
import java.util.Map;

/**
 * Everything Java computed for one press of Tính. Immutable; the snapshot store keeps it for the chat.
 *
 * @param salesSummary BR-10 summary, null unless the source is csv
 */
public record AnalysisResult(AnalyzeRequest request, Map<Scenario, ScenarioResult> scenarios,
                             DemandSourceInfo demandSourceInfo, SalesSummary salesSummary,
                             List<Fact> facts, List<Warning> warnings) {

    /**
     * FR-07 / BR-07 notices the UI shows next to the table.
     *
     * @param code     NEGATIVE_MARGIN (option loses money in that scenario) or WINNER_DIFFERS_BY_SCENARIO
     * @param scenario null for WINNER_DIFFERS_BY_SCENARIO
     * @param option   A or B for NEGATIVE_MARGIN, otherwise null
     */
    public record Warning(String code, Scenario scenario, String option) {
    }

    public ScenarioResult scenario(Scenario s) {
        return scenarios.get(s);
    }

    /** BR-08: A is a valid baseline (same for every scenario, it depends only on budget and shelf life). */
    public boolean baselineValid() {
        return scenarios.get(Scenario.BASE).conditions() != null;
    }
}
