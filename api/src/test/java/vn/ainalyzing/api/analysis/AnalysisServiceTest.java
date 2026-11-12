package vn.ainalyzing.api.analysis;

import org.junit.jupiter.api.Test;
import vn.ainalyzing.api.Fixtures;
import vn.ainalyzing.api.analysis.AnalysisResult.Warning;
import vn.ainalyzing.api.analysis.AnalyzeRequest.DemandSource;
import vn.ainalyzing.api.analysis.AnalyzeRequest.ManualDemand;
import vn.ainalyzing.api.analysis.ScenarioResult.Winner;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Demand sources, Phụ lục B facts and FR-07 warnings. The FEFO numbers themselves are in PurchaseCalculatorTest. */
class AnalysisServiceTest {

    static Map<String, Fact> byId(AnalysisResult r) {
        return r.facts().stream().collect(Collectors.toMap(Fact::id, Function.identity()));
    }

    @Test
    void demoUsesSeededSeriesAndFormPrices() {
        AnalysisResult r = Fixtures.std();
        assertArrayEquals(Fixtures.BASE, r.scenario(Scenario.BASE).demand());
        assertEquals("demo", r.demandSourceInfo().source());
        assertTrue(r.demandSourceInfo().synthetic());
        assertEquals(Winner.A, r.scenario(Scenario.BASE).winner());
        assertEquals(Winner.B, r.scenario(Scenario.FAST).winner());
        assertNull(r.salesSummary());
    }

    @Test
    void unknownDemoCodeIsRejected() {
        AnalyzeRequest r = Fixtures.demo(5, 432_000);
        AnalyzeRequest bad = new AnalyzeRequest(r.sku(), r.receiveDate(), r.sellPriceVnd(), r.budgetVnd(), 0, null,
                r.quoteA(), r.quoteB(), DemandSource.DEMO, null, "NOPE");
        assertThrows(UnknownDemoCaseException.class, () -> Fixtures.service().analyze(bad, null));
    }

    @Test
    void manualGivesFlatSeries() {
        AnalyzeRequest r = Fixtures.demo(5, 432_000);
        AnalyzeRequest manual = new AnalyzeRequest(r.sku(), r.receiveDate(), r.sellPriceVnd(), r.budgetVnd(), 0, null,
                r.quoteA(), r.quoteB(), DemandSource.MANUAL, new ManualDemand(4, 6, 8), null);
        AnalysisResult res = Fixtures.service().analyze(manual, null);
        assertArrayEquals(Fixtures.twoWeeks(6, 6, 6, 6, 6, 6, 6), res.scenario(Scenario.BASE).demand());
        assertEquals(56L, byId(res).get("F_DEMAND_14D_SLOW").longValue());
        assertEquals("manual", res.demandSourceInfo().source());
    }

    @Test
    void csvGivesA5SeriesAndSummaryFacts() throws IOException {
        AnalysisResult r = Fixtures.csv();
        assertArrayEquals(Fixtures.SLOW, r.scenario(Scenario.SLOW).demand());
        assertArrayEquals(Fixtures.FAST, r.scenario(Scenario.FAST).demand());
        assertEquals(56, r.demandSourceInfo().daysUsed());
        assertEquals(3, r.demandSourceInfo().inStockSamplesByWeekday().get("SAT"));
        Map<String, Fact> f = byId(r);
        assertEquals(5L, f.get("F_SAT_STOCKOUT_DAYS").longValue());
        assertEquals(8L, f.get("F_SAT_DAYS").longValue());
        assertTrue(f.containsKey("F_TREND_4W_PCT"));
        assertTrue(r.facts().size() >= 65 && r.facts().size() <= 85, "about 70 facts with CSV, got " + r.facts().size());
    }

    @Test
    void appendixBFactsForTheStandardCase() {
        AnalysisResult r = Fixtures.std();
        Map<String, Fact> f = byId(r);
        assertTrue(r.facts().size() >= 38 && r.facts().size() <= 60, "about 40 facts without CSV, got " + r.facts().size());
        for (String id : List.of("F_A_COST", "F_B_COST", "F_BUDGET", "F_DEMAND_14D_BASE", "F_A_SOLD_BASE", "F_B_SOLD_BASE",
                "F_A_EXPIRED_BASE", "F_B_EXPIRED_BASE", "F_A_SHORT_BASE", "F_B_SHORT_BASE", "F_A_MARGIN_BASE",
                "F_B_MARGIN_BASE", "F_MARGIN_GAP_BASE", "F_BREAKEVEN_TOTAL_BASE", "F_MAX_WIN_TOTAL_BASE", "F_B_MIN_DAYS_BASE")) {
            assertTrue(f.containsKey(id), id);
        }
        assertEquals(76_000L, f.get("F_A_MARGIN_BASE").longValue());
        assertEquals(404_000L, f.get("F_BREAKEVEN_TOTAL_BASE").longValue());
        assertEquals(Fact.VND, f.get("F_MAX_WIN_TOTAL_BASE").unit());
        assertEquals(Scenario.BASE, f.get("F_MAX_WIN_TOTAL_BASE").scenario());
        assertEquals("A", f.get("F_WINNER_BASE").value());
        assertFalse(f.containsKey("F_B_OVER_BUDGET"));
        assertFalse(f.containsKey("F_DAYS_TOTAL"), "CSV facts only with a CSV");
    }

    @Test
    void pendingQuoteHasNoInventedNumbers() {
        Map<String, Fact> f = byId(Fixtures.pending());
        assertFalse(f.containsKey("F_B_MARGIN_BASE"));
        assertFalse(f.containsKey("F_B_DAYS"));
        assertFalse(f.containsKey("F_MARGIN_GAP_BASE"));
        assertFalse(f.containsKey("F_BREAKEVEN_TOTAL_BASE"));
        assertEquals(6L, f.get("F_B_MIN_DAYS_BASE").longValue());
        assertEquals("SHELF_LIFE_PENDING", f.get("F_B_STATUS").value());
    }

    @Test
    void overBudgetFact() {
        Map<String, Fact> f = byId(Fixtures.budget400k());
        assertEquals(32_000L, f.get("F_B_OVER_BUDGET").longValue());
        assertEquals("OVER_BUDGET", f.get("F_B_STATUS").value());
    }

    @Test
    void warningsForLossAndChangingWinner() {
        List<Warning> w = Fixtures.std().warnings();
        assertTrue(w.contains(new Warning("NEGATIVE_MARGIN", Scenario.SLOW, "B")));
        assertTrue(w.contains(new Warning("WINNER_DIFFERS_BY_SCENARIO", null, null)));
        assertFalse(Fixtures.budget400k().warnings().contains(new Warning("WINNER_DIFFERS_BY_SCENARIO", null, null)));
    }
}
