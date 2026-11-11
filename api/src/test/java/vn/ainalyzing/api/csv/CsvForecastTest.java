package vn.ainalyzing.api.csv;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvForecastTest {

    static final LocalDate D = LocalDate.of(2026, 11, 13); // Friday

    static String demo(String name) throws IOException {
        return Files.readString(Path.of("..", "data", "demo", name));
    }

    @Test
    void demoCsvGivesTheSeriesInSpecA5() throws IOException {
        List<SalesDay> days = SalesCsvParser.parse(demo("yogurt_56d.csv"), D);
        DemandForecaster.Forecast f = DemandForecaster.forecast(days, D);

        assertArrayEquals(new int[]{5, 5, 5, 6, 6, 2, 5, 5, 5, 5, 6, 6, 2, 5}, f.slow());
        assertArrayEquals(new int[]{6, 6, 6, 7, 7, 3, 6, 6, 6, 6, 7, 7, 3, 6}, f.base());
        assertArrayEquals(new int[]{7, 7, 7, 8, 8, 4, 7, 7, 7, 7, 8, 8, 4, 7}, f.fast());
        assertEquals(56, f.daysUsed());
        assertEquals(3, f.inStockSamplesByWeekday().get(DayOfWeek.SATURDAY));
        assertTrue(f.stockoutMarked());
    }

    @Test
    void demoCsvSummaryShowsSaturdaySellOutsAndRisingTrend() throws IOException {
        SalesSummaryBuilder.SalesSummary s = SalesSummaryBuilder.build(SalesCsvParser.parse(demo("yogurt_56d.csv"), D), D);
        assertEquals(56, s.daysTotal());
        assertEquals(5, s.daysStockout());
        assertEquals(5, s.byWeekday().get(DayOfWeek.SATURDAY).stockoutDays());
        assertEquals(8, s.byWeekday().get(DayOfWeek.SATURDAY).days());
        assertTrue(s.trend4wPct() > 0, "trend should rise");
        assertEquals(0, s.anomalyCount());
    }

    @Test
    void trendCsvHasNoSellOutsButRisesAboutAQuarter() throws IOException {
        SalesSummaryBuilder.SalesSummary s = SalesSummaryBuilder.build(SalesCsvParser.parse(demo("yogurt_trend_56d.csv"), D), D);
        assertEquals(0, s.daysStockout());
        assertTrue(s.trend4wPct() >= 20 && s.trend4wPct() <= 35, "trend was " + s.trend4wPct());
    }

    @Test
    void reportsEveryBadRowWithItsLineNumber() {
        String csv = """
                date,units_sold,stock_status
                2026-11-01,5,in_stock
                2026-11-01,6,in_stock
                2026-11-02,-1,in_stock
                2026-11-03,4,sold_out
                2026-11-13,4,in_stock
                """;
        CsvException e = assertThrows(CsvException.class, () -> SalesCsvParser.parse(csv, D));
        assertEquals(List.of(3, 4, 5, 6), e.problems().stream().map(CsvException.Problem::row).toList());
    }

    @Test
    void rejectsWrongHeader() {
        CsvException e = assertThrows(CsvException.class, () -> SalesCsvParser.parse("day,sold\n2026-11-01,5\n", D));
        assertEquals(1, e.problems().getFirst().row());
    }

    @Test
    void missingSamplesListsEachWeekday() {
        StringBuilder csv = new StringBuilder(SalesCsvParser.HEADER + "\n");
        for (int i = 1; i <= 14; i++) { // only 2 of each weekday
            csv.append(D.minusDays(i)).append(",5,in_stock\n");
        }
        CsvException e = assertThrows(CsvException.class, () -> DemandForecaster.forecast(SalesCsvParser.parse(csv.toString(), D), D));
        assertEquals(7, e.problems().size());
        assertEquals("INSUFFICIENT_SAMPLES", e.problems().getFirst().code());
    }

    @Test
    void fileWithoutStockMarksUsesEveryDay() {
        StringBuilder csv = new StringBuilder(SalesCsvParser.HEADER + "\n");
        for (int i = 1; i <= 21; i++) {
            csv.append(D.minusDays(i)).append(",").append(i % 3 + 4).append(",unknown\n");
        }
        DemandForecaster.Forecast f = DemandForecaster.forecast(SalesCsvParser.parse(csv.toString(), D), D);
        assertFalse(f.stockoutMarked());
        assertEquals(3, f.inStockSamplesByWeekday().get(DayOfWeek.MONDAY));
    }

    @Test
    void percentileMatchesExcelPercentileInc() {
        assertEquals(5.0, DemandForecaster.percentile(List.of(4, 6, 7), 0.25));
        assertEquals(6.5, DemandForecaster.percentile(List.of(4, 6, 7), 0.75));
        assertEquals(7, DemandForecaster.roundHalfUp(6.5));
        assertNull(SalesSummaryBuilder.build(List.of(new SalesDay(D.minusDays(1), 3, SalesDay.Status.IN_STOCK)), D).trend4wPct());
    }
}
