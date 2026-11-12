package vn.ainalyzing.api.analysis;

import java.util.Map;

/**
 * Spec A.2 demandSourceInfo (BR-09: where the demand came from).
 *
 * @param source                  csv, manual or demo
 * @param daysUsed                CSV days inside the 56-day window, null for manual / demo
 * @param inStockSamplesByWeekday MON–SUN sample counts used per weekday, null for manual / demo
 * @param demoCaseCode            the seeded case, null unless the source is demo
 * @param synthetic               true when the demand comes from a seeded synthetic case
 */
public record DemandSourceInfo(String source, Integer daysUsed, Map<String, Integer> inStockSamplesByWeekday,
                               String demoCaseCode, boolean synthetic) {
}
