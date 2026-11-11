package vn.ainalyzing.api.analysis;

/** Result of one demand scenario (Spec A.2: scenarios.S and conditions.S). */
public record ScenarioResult(int[] demand, OptionResult a, OptionResult b,
                             Winner winner, Long marginGapVnd, Conditions conditions) {

    public enum Winner { A, B, TIE, NONE }

    public enum BlockReason { OVER_BUDGET, SHELF_LIFE_PENDING }

    /** Unit and money fields are null when the quote cannot be simulated (shelf life pending). */
    public record OptionResult(boolean eligible, BlockReason blockReason, long costVnd,
                               Integer unitsSold, Integer unitsShort, Integer unitsExpired,
                               Long revenueVnd, Long marginVnd) {
    }

    /** BR-08. Null as a whole when A is not a valid baseline. */
    public record Conditions(Long breakEvenTotalVnd, Long maxWinningTotalVnd,
                             Integer minSellableDaysAtQuotedPrice, boolean pending) {
    }
}
