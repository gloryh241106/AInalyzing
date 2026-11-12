package vn.ainalyzing.api.analysis;

import vn.ainalyzing.api.analysis.ScenarioResult.BlockReason;
import vn.ainalyzing.api.analysis.ScenarioResult.Conditions;
import vn.ainalyzing.api.analysis.ScenarioResult.OptionResult;
import vn.ainalyzing.api.csv.SalesSummaryBuilder.SalesSummary;
import vn.ainalyzing.api.csv.SalesSummaryBuilder.WeekdayStats;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Spec Phụ lục B. Ids follow F_&lt;object&gt;_&lt;metric&gt;[_&lt;scenario&gt;]. A fact whose Java value is null (for example
 * the margin of a quote whose shelf life is pending) is left out, so Groq can only cite numbers that exist.
 * <p>
 * Additions to Phụ lục B (see docs/decisions.md D-07): F_SELL_PRICE, F_OPENING_UNITS, F_OPENING_DAYS,
 * F_A_UNITS / F_B_UNITS, F_A_DAYS / F_B_DAYS, F_A_STATUS / F_B_STATUS, F_A_OVER_BUDGET and F_WINNER_S. They let the
 * model and the templates name the decision without re-deriving it.
 */
public final class FactsBuilder {

    private FactsBuilder() {
    }

    public static List<Fact> build(AnalyzeRequest req, Map<Scenario, ScenarioResult> results, SalesSummary summary) {
        List<Fact> out = new ArrayList<>();
        ScenarioResult base = results.get(Scenario.BASE);

        add(out, "F_SELL_PRICE", req.sellPriceVnd(), Fact.VND_PER_UNIT, null);
        add(out, "F_BUDGET", req.budgetVnd(), Fact.VND, null);
        add(out, "F_OPENING_UNITS", (long) req.openingUnits(), Fact.UNITS, null);
        if (req.openingUnits() > 0) {
            add(out, "F_OPENING_DAYS", req.openingSellableDays().longValue(), Fact.DAYS, null);
        }
        quoteFacts(out, "A", req.quoteA(), base.a(), req.budgetVnd());
        quoteFacts(out, "B", req.quoteB(), base.b(), req.budgetVnd());

        if (summary != null) {
            csvFacts(out, summary);
        }

        for (Scenario s : Scenario.values()) {
            ScenarioResult r = results.get(s);
            add(out, "F_DEMAND_14D_" + s, (long) Arrays.stream(r.demand()).sum(), Fact.UNITS, s);
            optionFacts(out, "A", r.a(), s);
            optionFacts(out, "B", r.b(), s);
            add(out, "F_MARGIN_GAP_" + s, r.marginGapVnd(), Fact.VND, s);
            add(out, "F_WINNER_" + s, r.winner().name(), Fact.CODE, s);
            Conditions c = r.conditions();
            if (c != null) {
                add(out, "F_BREAKEVEN_TOTAL_" + s, c.breakEvenTotalVnd(), Fact.VND, s);
                add(out, "F_MAX_WIN_TOTAL_" + s, c.maxWinningTotalVnd(), Fact.VND, s);
                add(out, "F_B_MIN_DAYS_" + s, c.minSellableDaysAtQuotedPrice() == null ? null
                        : c.minSellableDaysAtQuotedPrice().longValue(), Fact.DAYS, s);
            }
        }
        return List.copyOf(out);
    }

    private static void quoteFacts(List<Fact> out, String name, Quote q, OptionResult base, long budget) {
        add(out, "F_" + name + "_UNITS", (long) q.units(), Fact.UNITS, null);
        add(out, "F_" + name + "_COST", q.costVnd(), Fact.VND, null);
        add(out, "F_" + name + "_DAYS", q.sellableDays() == null ? null : q.sellableDays().longValue(), Fact.DAYS, null);
        BlockReason block = base.blockReason();
        add(out, "F_" + name + "_STATUS", block == null ? "ELIGIBLE" : block.name(), Fact.CODE, null);
        if (q.costVnd() > budget) {
            add(out, "F_" + name + "_OVER_BUDGET", q.costVnd() - budget, Fact.VND, null);
        }
    }

    private static void optionFacts(List<Fact> out, String name, OptionResult o, Scenario s) {
        add(out, "F_" + name + "_SOLD_" + s, toLong(o.unitsSold()), Fact.UNITS, s);
        add(out, "F_" + name + "_SHORT_" + s, toLong(o.unitsShort()), Fact.UNITS, s);
        add(out, "F_" + name + "_EXPIRED_" + s, toLong(o.unitsExpired()), Fact.UNITS, s);
        add(out, "F_" + name + "_MARGIN_" + s, o.marginVnd(), Fact.VND, s);
    }

    private static void csvFacts(List<Fact> out, SalesSummary s) {
        add(out, "F_DAYS_TOTAL", (long) s.daysTotal(), Fact.DAYS, null);
        add(out, "F_DAYS_IN_STOCK", (long) s.daysInStock(), Fact.DAYS, null);
        add(out, "F_DAYS_STOCKOUT", (long) s.daysStockout(), Fact.DAYS, null);
        add(out, "F_DAYS_UNKNOWN", (long) s.daysUnknown(), Fact.DAYS, null);
        for (DayOfWeek d : DayOfWeek.values()) {
            WeekdayStats w = s.byWeekday().get(d);
            String code = Weekdays.code(d);
            add(out, "F_" + code + "_DAYS", (long) w.days(), Fact.DAYS, null);
            add(out, "F_" + code + "_STOCKOUT_DAYS", (long) w.stockoutDays(), Fact.DAYS, null);
            add(out, "F_" + code + "_AVG_IN_STOCK", w.avgInStock(), Fact.UNITS_PER_DAY, null);
        }
        add(out, "F_TREND_4W_PCT", s.trend4wPct() == null ? null : s.trend4wPct().longValue(), Fact.PERCENT, null);
        if (s.anomalyCount() > 0) {
            add(out, "F_ANOMALY_COUNT", (long) s.anomalyCount(), Fact.DAYS, null);
            add(out, "F_ANOMALY_MAX_UNITS", s.anomalyMaxUnits().longValue(), Fact.UNITS, null);
        }
    }

    private static Long toLong(Integer i) {
        return i == null ? null : i.longValue();
    }

    private static void add(List<Fact> out, String id, Object value, String unit, Scenario s) {
        if (value != null) {
            out.add(new Fact(id, value, unit, s));
        }
    }
}
