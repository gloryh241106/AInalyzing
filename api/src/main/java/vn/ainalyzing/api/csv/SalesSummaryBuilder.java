package vn.ainalyzing.api.csv;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * BR-10: the numbers Groq reads instead of raw CSV rows. Fact IDs (Spec Phụ lục B) are built from this record
 * by the llm package.
 */
public final class SalesSummaryBuilder {

    private SalesSummaryBuilder() {
    }

    /** avgInStock is null when the weekday has no in_stock day. */
    public record WeekdayStats(int days, int stockoutDays, Double avgInStock) {
    }

    /** trend4wPct is null unless the file covers the full 56 days; anomalyMaxUnits is null when there is none. */
    public record SalesSummary(int daysTotal, int daysInStock, int daysStockout, int daysUnknown,
                               Map<DayOfWeek, WeekdayStats> byWeekday, Integer trend4wPct,
                               int anomalyCount, Integer anomalyMaxUnits) {
    }

    public static SalesSummary build(List<SalesDay> all, LocalDate receiveDate) {
        List<SalesDay> days = DemandForecaster.window(all, receiveDate);
        int inStock = count(days, SalesDay.Status.IN_STOCK);
        int stockout = count(days, SalesDay.Status.STOCKOUT);

        Map<DayOfWeek, WeekdayStats> byWeekday = new EnumMap<>(DayOfWeek.class);
        int anomalyCount = 0;
        Integer anomalyMax = null;
        for (DayOfWeek w : DayOfWeek.values()) {
            List<SalesDay> sameDay = days.stream().filter(d -> d.date().getDayOfWeek() == w).toList();
            List<Integer> sold = inStockUnits(sameDay);
            Double avg = sold.isEmpty() ? null
                    : Math.round(sold.stream().mapToInt(Integer::intValue).average().orElseThrow() * 10) / 10.0;
            byWeekday.put(w, new WeekdayStats(sameDay.size(), count(sameDay, SalesDay.Status.STOCKOUT), avg));

            if (sold.size() >= DemandForecaster.MIN_SAMPLES) {
                double q1 = DemandForecaster.percentile(sold, 0.25);
                double q3 = DemandForecaster.percentile(sold, 0.75);
                double limit = q3 + 1.5 * (q3 - q1);
                for (int u : sold) {
                    if (u > limit) {
                        anomalyCount++;
                        anomalyMax = anomalyMax == null ? u : Math.max(anomalyMax, u);
                    }
                }
            }
        }
        return new SalesSummary(days.size(), inStock, stockout, days.size() - inStock - stockout,
                byWeekday, trend(days, receiveDate), anomalyCount, anomalyMax);
    }

    /** Average in_stock units of the last 4 weeks against the 4 weeks before, in percent. */
    private static Integer trend(List<SalesDay> days, LocalDate d) {
        LocalDate start = d.minusDays(DemandForecaster.WINDOW_DAYS);
        if (days.stream().noneMatch(x -> x.date().equals(start))) {
            return null;
        }
        LocalDate split = d.minusDays(28);
        List<Integer> recent = inStockUnits(days.stream().filter(x -> !x.date().isBefore(split)).toList());
        List<Integer> before = inStockUnits(days.stream().filter(x -> x.date().isBefore(split)).toList());
        double a = recent.stream().mapToInt(Integer::intValue).average().orElse(0);
        double b = before.stream().mapToInt(Integer::intValue).average().orElse(0);
        if (recent.isEmpty() || b == 0) {
            return null;
        }
        return (int) Math.round((a / b - 1) * 100);
    }

    private static List<Integer> inStockUnits(List<SalesDay> days) {
        List<Integer> out = new ArrayList<>();
        for (SalesDay x : days) {
            if (x.status() == SalesDay.Status.IN_STOCK) {
                out.add(x.unitsSold());
            }
        }
        return out;
    }

    private static int count(List<SalesDay> days, SalesDay.Status s) {
        return (int) days.stream().filter(x -> x.status() == s).count();
    }
}
