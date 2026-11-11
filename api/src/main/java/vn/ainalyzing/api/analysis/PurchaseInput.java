package vn.ainalyzing.api.analysis;

/**
 * Everything one scenario needs (Spec A.1, minus the demand source).
 * openingUnits may be 0; when it is above 0, openingSellableDays is 1–14 (BR-02).
 */
public record PurchaseInput(long sellPriceVnd, long budgetVnd,
                            int openingUnits, Integer openingSellableDays,
                            Quote quoteA, Quote quoteB) {
}
