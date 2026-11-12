package vn.ainalyzing.api.web;

import vn.ainalyzing.api.analysis.AnalysisResult;
import vn.ainalyzing.api.analysis.AnalysisResult.Warning;
import vn.ainalyzing.api.analysis.AnalysisSnapshot;
import vn.ainalyzing.api.analysis.AnalyzeRequest;
import vn.ainalyzing.api.analysis.DemandSourceInfo;
import vn.ainalyzing.api.analysis.Fact;
import vn.ainalyzing.api.analysis.Quote;
import vn.ainalyzing.api.analysis.Scenario;
import vn.ainalyzing.api.analysis.ScenarioResult;
import vn.ainalyzing.api.analysis.ScenarioResult.Conditions;
import vn.ainalyzing.api.analysis.ScenarioResult.OptionResult;
import vn.ainalyzing.api.analysis.ScenarioResult.Winner;
import vn.ainalyzing.api.analysis.Weekdays;
import vn.ainalyzing.api.csv.SalesSummaryBuilder.SalesSummary;
import vn.ainalyzing.api.csv.SalesSummaryBuilder.WeekdayStats;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spec A.2 plus three additive fields (docs/decisions.md D-05): input echoes the validated request so the table is
 * tied to the inputs of its own run (FR-09), expiresAt tells the UI when the chat will answer stale_analysis, and
 * warnings carries the FR-07 / BR-07 notices.
 */
public record AnalyzeResponse(String analysisId, String expiresAt, Scenario defaultScenario,
                              Map<String, Object> input, DemandSourceInfo demandSourceInfo,
                              Map<Scenario, ScenarioView> scenarios, Map<Scenario, Conditions> conditions,
                              SalesSummaryView salesSummary, List<Fact> facts, List<Warning> warnings) {

    public record ScenarioView(int[] demand, Map<String, OptionResult> options, Winner winner, Long marginGapVnd) {
    }

    /** BR-10 summary with weekdays keyed MON–SUN. */
    public record SalesSummaryView(int daysTotal, int daysInStock, int daysStockout, int daysUnknown,
                                   Map<String, WeekdayStats> byWeekday, Integer trend4wPct,
                                   int anomalyCount, Integer anomalyMaxUnits) {
    }

    public static AnalyzeResponse of(AnalysisSnapshot snapshot) {
        AnalysisResult r = snapshot.result();
        Map<Scenario, ScenarioView> scenarios = new EnumMap<>(Scenario.class);
        Map<Scenario, Conditions> conditions = new EnumMap<>(Scenario.class);
        for (Scenario s : Scenario.values()) {
            ScenarioResult x = r.scenario(s);
            Map<String, OptionResult> options = new LinkedHashMap<>();
            options.put("A", x.a());
            options.put("B", x.b());
            scenarios.put(s, new ScenarioView(x.demand(), options, x.winner(), x.marginGapVnd()));
            conditions.put(s, x.conditions());
        }
        return new AnalyzeResponse(snapshot.id(), snapshot.expiresAt().toString(), Scenario.BASE,
                input(r.request()), r.demandSourceInfo(), scenarios, r.baselineValid() ? conditions : null,
                summary(r.salesSummary()), r.facts(), r.warnings());
    }

    private static SalesSummaryView summary(SalesSummary s) {
        if (s == null) {
            return null;
        }
        return new SalesSummaryView(s.daysTotal(), s.daysInStock(), s.daysStockout(), s.daysUnknown(),
                Weekdays.byCode(s.byWeekday(), w -> w), s.trend4wPct(), s.anomalyCount(), s.anomalyMaxUnits());
    }

    /** Same shape as A.1 so React can refill the form from a result. */
    private static Map<String, Object> input(AnalyzeRequest q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sku", q.sku());
        m.put("receiveDate", q.receiveDate().toString());
        m.put("sellPriceVnd", q.sellPriceVnd());
        m.put("budgetVnd", q.budgetVnd());
        Map<String, Object> opening = new LinkedHashMap<>();
        opening.put("units", q.openingUnits());
        opening.put("sellableDays", q.openingSellableDays());
        m.put("openingStock", opening);
        m.put("quoteA", quote(q.quoteA()));
        m.put("quoteB", quote(q.quoteB()));
        m.put("demandSource", q.demandSource().code());
        m.put("manualDemand", q.manualDemand());
        m.put("demoCaseCode", q.demoCaseCode());
        return m;
    }

    private static Map<String, Object> quote(Quote x) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("units", x.units());
        m.put("goodsVnd", x.goodsVnd());
        m.put("deliveryVnd", x.deliveryVnd());
        m.put("sellableDays", x.sellableDays());
        return m;
    }
}
