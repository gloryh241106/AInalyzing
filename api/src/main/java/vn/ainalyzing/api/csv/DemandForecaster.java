package vn.ainalyzing.api.csv;

import vn.ainalyzing.api.analysis.PurchaseCalculator;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * BR-04: slow/base/fast are P25/P50/P75 of the in_stock days of the same weekday (linear interpolation as
 * Excel PERCENTILE.INC, rounded half up), using at most 56 days before D. A file with no in_stock or stockout
 * marks at all (every day unknown) uses every day as a sample.
 */
public final class DemandForecaster {

    public static final int WINDOW_DAYS = 56;
    public static final int MIN_SAMPLES = 3;

    private DemandForecaster() {
    }

    public record Forecast(int[] slow, int[] base, int[] fast, int daysUsed,
                           Map<DayOfWeek, Integer> inStockSamplesByWeekday, boolean stockoutMarked) {
    }

    public static Forecast forecast(List<SalesDay> all, LocalDate receiveDate) {
        List<SalesDay> days = window(all, receiveDate);
        boolean marked = days.stream().anyMatch(d -> d.status() != SalesDay.Status.UNKNOWN);

        Map<DayOfWeek, List<Integer>> samples = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek w : DayOfWeek.values()) {
            samples.put(w, new ArrayList<>());
        }
        for (SalesDay d : days) {
            if (!marked || d.status() == SalesDay.Status.IN_STOCK) {
                samples.get(d.date().getDayOfWeek()).add(d.unitsSold());
            }
        }

        List<CsvException.Problem> missing = new ArrayList<>();
        Map<DayOfWeek, Integer> counts = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek w : DayOfWeek.values()) {
            int n = samples.get(w).size();
            counts.put(w, n);
            if (n < MIN_SAMPLES) {
                missing.add(new CsvException.Problem("INSUFFICIENT_SAMPLES",
                        w + " chỉ có " + n + " ngày dùng được, cần ít nhất " + MIN_SAMPLES, null));
            }
        }
        if (!missing.isEmpty()) {
            throw new CsvException(missing);
        }

        int h = PurchaseCalculator.HORIZON_DAYS;
        int[] slow = new int[h];
        int[] base = new int[h];
        int[] fast = new int[h];
        for (int i = 0; i < h; i++) {
            List<Integer> s = samples.get(receiveDate.plusDays(i).getDayOfWeek());
            slow[i] = roundHalfUp(percentile(s, 0.25));
            base[i] = roundHalfUp(percentile(s, 0.50));
            fast[i] = roundHalfUp(percentile(s, 0.75));
        }
        return new Forecast(slow, base, fast, days.size(), counts, marked);
    }

    static List<SalesDay> window(List<SalesDay> all, LocalDate receiveDate) {
        LocalDate first = receiveDate.minusDays(WINDOW_DAYS);
        return all.stream().filter(d -> !d.date().isBefore(first) && d.date().isBefore(receiveDate)).toList();
    }

    /** Excel PERCENTILE.INC / R type 7. */
    static double percentile(List<Integer> values, double p) {
        List<Integer> v = values.stream().sorted().toList();
        double h = (v.size() - 1) * p;
        int lo = (int) Math.floor(h);
        int hi = Math.min(lo + 1, v.size() - 1);
        return v.get(lo) + (h - lo) * (v.get(hi) - v.get(lo));
    }

    static int roundHalfUp(double x) {
        return (int) Math.floor(x + 0.5);
    }
}
