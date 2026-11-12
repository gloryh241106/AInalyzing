package vn.ainalyzing.api.analysis;

import java.time.LocalDate;
import java.util.Locale;

/**
 * A validated Spec A.1 request. Built only by {@code web.AnalyzeRequestReader}, so every rule in A.1 / BR-01 / BR-02
 * already holds: quoteB.units &gt; quoteA.units, sellable days 1–14 or null, openingSellableDays set when
 * openingUnits &gt; 0, manualDemand set and ordered when the source is manual, demoCaseCode set when demo.
 */
public record AnalyzeRequest(String sku, LocalDate receiveDate, long sellPriceVnd, long budgetVnd,
                             int openingUnits, Integer openingSellableDays,
                             Quote quoteA, Quote quoteB,
                             DemandSource demandSource, ManualDemand manualDemand, String demoCaseCode) {

    public enum DemandSource {
        CSV, MANUAL, DEMO;

        /** Wire value: csv, manual or demo. */
        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Units per day, 0 ≤ slow ≤ base ≤ fast. */
    public record ManualDemand(int slow, int base, int fast) {
    }

    public PurchaseInput purchaseInput() {
        return new PurchaseInput(sellPriceVnd, budgetVnd, openingUnits, openingSellableDays, quoteA, quoteB);
    }
}
