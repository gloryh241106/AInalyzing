package vn.ainalyzing.api.analysis;

import vn.ainalyzing.api.analysis.ScenarioResult.BlockReason;
import vn.ainalyzing.api.analysis.ScenarioResult.Conditions;
import vn.ainalyzing.api.analysis.ScenarioResult.OptionResult;
import vn.ainalyzing.api.analysis.ScenarioResult.Winner;

import java.util.Arrays;

/** BR-05 to BR-08: FEFO over 14 days, ranking and negotiation conditions for one demand scenario. */
public final class PurchaseCalculator {

    public static final int HORIZON_DAYS = 14;

    private PurchaseCalculator() {
    }

    public static ScenarioResult compare(PurchaseInput in, int[] demand) {
        if (demand.length != HORIZON_DAYS) {
            throw new IllegalArgumentException("demand must have " + HORIZON_DAYS + " days");
        }
        OptionResult a = option(in, in.quoteA(), demand);
        OptionResult b = option(in, in.quoteB(), demand);

        Winner winner;
        Long gap = null;
        if (a.eligible() && b.eligible()) {
            int byMargin = Long.compare(a.marginVnd(), b.marginVnd());
            if (byMargin != 0) {
                winner = byMargin > 0 ? Winner.A : Winner.B;
            } else if (a.costVnd() != b.costVnd()) {
                winner = a.costVnd() < b.costVnd() ? Winner.A : Winner.B; // same margin: spend less
            } else {
                winner = Winner.TIE;
            }
            gap = Math.abs(a.marginVnd() - b.marginVnd());
        } else if (a.eligible()) {
            winner = Winner.A;
        } else if (b.eligible()) {
            winner = Winner.B;
        } else {
            winner = Winner.NONE;
        }

        Conditions conditions = a.eligible() ? conditions(in, demand, a.marginVnd()) : null;
        return new ScenarioResult(demand.clone(), a, b, winner, gap, conditions);
    }

    private static OptionResult option(PurchaseInput in, Quote q, int[] demand) {
        long cost = q.costVnd();
        if (q.sellableDays() == null) {
            return new OptionResult(false, BlockReason.SHELF_LIFE_PENDING, cost, null, null, null, null, null);
        }
        Sim s = simulate(demand, in.openingUnits(), in.openingSellableDays(), q.units(), q.sellableDays());
        long revenue = s.sold() * in.sellPriceVnd();
        boolean overBudget = cost > in.budgetVnd();
        return new OptionResult(!overBudget, overBudget ? BlockReason.OVER_BUDGET : null, cost,
                s.sold(), s.shortUnits(), s.expired(), revenue, revenue - cost);
    }

    /** BR-08, called only when A is eligible. */
    private static Conditions conditions(PurchaseInput in, int[] demand, long marginA) {
        Quote b = in.quoteB();
        Integer minDays = null;
        if (b.costVnd() <= in.budgetVnd()) {
            for (int d = 1; d <= HORIZON_DAYS; d++) {
                if (margin(in, b.withSellableDays(d), demand) > marginA) {
                    minDays = d;
                    break;
                }
            }
        }
        if (b.sellableDays() == null) {
            return new Conditions(null, null, minDays, true);
        }
        long revenueB = simulate(demand, in.openingUnits(), in.openingSellableDays(),
                b.units(), b.sellableDays()).sold() * in.sellPriceVnd();
        long breakEven = revenueB - marginA;
        long maxWin = Math.min(in.budgetVnd(), breakEven - 1);
        return new Conditions(breakEven, maxWin < b.deliveryVnd() ? null : maxWin, minDays, false);
    }

    private static long margin(PurchaseInput in, Quote q, int[] demand) {
        Sim s = simulate(demand, in.openingUnits(), in.openingSellableDays(), q.units(), q.sellableDays());
        return s.sold() * in.sellPriceVnd() - q.costVnd();
    }

    record Sim(int sold, int shortUnits, int expired) {
    }

    /**
     * BR-05: day 0 is D. Each day sells from the lot that expires first; on equal expiry the opening lot goes first.
     * A lot with n sellable days sells on days 0..n-1 and whatever is left at the end of day n-1 expires.
     */
    static Sim simulate(int[] demand, int openingUnits, Integer openingDays, int newUnits, int newDays) {
        boolean hasOpening = openingUnits > 0;
        boolean openingFirst = hasOpening && openingDays <= newDays;
        int[] left = hasOpening
                ? (openingFirst ? new int[]{openingUnits, newUnits} : new int[]{newUnits, openingUnits})
                : new int[]{newUnits};
        int[] days = hasOpening
                ? (openingFirst ? new int[]{openingDays, newDays} : new int[]{newDays, openingDays})
                : new int[]{newDays};

        int sold = 0;
        int expired = 0;
        for (int day = 0; day < demand.length; day++) {
            int want = demand[day];
            for (int i = 0; i < left.length && want > 0; i++) {
                if (day < days[i]) {
                    int take = Math.min(want, left[i]);
                    left[i] -= take;
                    want -= take;
                    sold += take;
                }
            }
            for (int i = 0; i < left.length; i++) {
                if (days[i] == day + 1) {
                    expired += left[i];
                    left[i] = 0;
                }
            }
        }
        return new Sim(sold, Arrays.stream(demand).sum() - sold, expired);
    }
}
