package vn.ainalyzing.api.analysis;

import org.junit.jupiter.api.Test;
import vn.ainalyzing.api.analysis.ScenarioResult.BlockReason;
import vn.ainalyzing.api.analysis.ScenarioResult.OptionResult;
import vn.ainalyzing.api.analysis.ScenarioResult.Winner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Check numbers from Specification Phụ lục A.2 (YOGURT_STD, D = Friday 13/11/2026). */
class PurchaseCalculatorTest {

    static final int[] SLOW = twoWeeks(5, 5, 5, 6, 6, 2, 5);
    static final int[] BASE = twoWeeks(6, 6, 6, 7, 7, 3, 6);
    static final int[] FAST = twoWeeks(7, 7, 7, 8, 8, 4, 7);

    static final Quote A = new Quote(24, 284_000, 0, 5);
    static final Quote B5 = new Quote(48, 432_000, 0, 5);

    static int[] twoWeeks(int... week) {
        int[] d = new int[14];
        for (int i = 0; i < 14; i++) {
            d[i] = week[i % 7];
        }
        return d;
    }

    static PurchaseInput std(Quote b, long budget) {
        return new PurchaseInput(15_000, budget, 0, null, A, b);
    }

    static void assertOption(OptionResult o, int sold, int shortUnits, int expired, long margin) {
        assertEquals(sold, o.unitsSold(), "sold");
        assertEquals(shortUnits, o.unitsShort(), "short");
        assertEquals(expired, o.unitsExpired(), "expired");
        assertEquals(margin, o.marginVnd(), "margin");
        assertEquals(sold * 15_000L, o.revenueVnd(), "revenue");
    }

    @Test
    void standardCaseAllScenarios() {
        ScenarioResult slow = PurchaseCalculator.compare(std(B5, 432_000), SLOW);
        assertOption(slow.a(), 24, 44, 0, 76_000);
        assertOption(slow.b(), 27, 41, 21, -27_000);
        assertEquals(Winner.A, slow.winner());
        assertEquals(103_000L, slow.marginGapVnd());
        assertEquals(329_000L, slow.conditions().breakEvenTotalVnd());
        assertEquals(328_999L, slow.conditions().maxWinningTotalVnd());
        assertEquals(7, slow.conditions().minSellableDaysAtQuotedPrice());

        ScenarioResult base = PurchaseCalculator.compare(std(B5, 432_000), BASE);
        assertOption(base.a(), 24, 58, 0, 76_000);
        assertOption(base.b(), 32, 50, 16, 48_000);
        assertEquals(Winner.A, base.winner());
        assertEquals(28_000L, base.marginGapVnd());
        assertEquals(404_000L, base.conditions().breakEvenTotalVnd());
        assertEquals(403_999L, base.conditions().maxWinningTotalVnd());
        assertEquals(6, base.conditions().minSellableDaysAtQuotedPrice());
        assertFalse(base.conditions().pending());

        ScenarioResult fast = PurchaseCalculator.compare(std(B5, 432_000), FAST);
        assertOption(fast.a(), 24, 72, 0, 76_000);
        assertOption(fast.b(), 37, 59, 11, 123_000);
        assertEquals(Winner.B, fast.winner());
        assertEquals(47_000L, fast.marginGapVnd());
        assertEquals(479_000L, fast.conditions().breakEvenTotalVnd());
        assertEquals(432_000L, fast.conditions().maxWinningTotalVnd());
        assertEquals(5, fast.conditions().minSellableDaysAtQuotedPrice());
    }

    @Test
    void sixDayBatch() {
        Quote b6 = B5.withSellableDays(6);
        assertEquals(3_000L, PurchaseCalculator.compare(std(b6, 432_000), SLOW).b().marginVnd());
        ScenarioResult base = PurchaseCalculator.compare(std(b6, 432_000), BASE);
        assertOption(base.b(), 35, 47, 13, 93_000);
        assertEquals(Winner.B, base.winner());
        assertEquals(17_000L, base.marginGapVnd());
        assertEquals(183_000L, PurchaseCalculator.compare(std(b6, 432_000), FAST).b().marginVnd());
    }

    @Test
    void budgetBlocksB() {
        ScenarioResult r = PurchaseCalculator.compare(std(B5.withSellableDays(6), 400_000), BASE);
        assertFalse(r.b().eligible());
        assertEquals(BlockReason.OVER_BUDGET, r.b().blockReason());
        assertEquals(Winner.A, r.winner());
        assertNull(r.marginGapVnd());
        assertEquals(400_000L, r.conditions().maxWinningTotalVnd());
        assertNull(r.conditions().minSellableDaysAtQuotedPrice());
    }

    @Test
    void tieAtBreakEvenPicksCheaperA() {
        ScenarioResult r = PurchaseCalculator.compare(std(B5.withGoodsVnd(404_000), 432_000), BASE);
        assertEquals(76_000L, r.b().marginVnd());
        assertEquals(Winner.A, r.winner());
        assertEquals(0L, r.marginGapVnd());
    }

    @Test
    void pendingShelfLifeShowsRequiredDays() {
        ScenarioResult r = PurchaseCalculator.compare(std(B5.withSellableDays(null), 432_000), BASE);
        assertEquals(BlockReason.SHELF_LIFE_PENDING, r.b().blockReason());
        assertNull(r.b().unitsSold());
        assertTrue(r.conditions().pending());
        assertEquals(6, r.conditions().minSellableDaysAtQuotedPrice());
        assertNull(r.conditions().breakEvenTotalVnd());
    }

    @Test
    void openingStockThatExpiresFirstSellsFirst() {
        // 10 old jars with 2 days left, new lot of 24 with 5 days, demand 6 per day.
        PurchaseCalculator.Sim s = PurchaseCalculator.simulate(twoWeeks(6, 6, 6, 6, 6, 6, 6), 10, 2, 24, 5);
        // Days 0-1 sell 12: 10 old + 2 new. Days 2-4 sell 18 new. 4 new jars expire after day 4.
        assertEquals(30, s.sold());
        assertEquals(4, s.expired());
    }

    @Test
    void oneDayLotSellsOnlyOnReceiveDay() {
        PurchaseCalculator.Sim s = PurchaseCalculator.simulate(BASE, 0, null, 10, 1);
        assertEquals(6, s.sold());
        assertEquals(4, s.expired());
    }
}
