package vn.ainalyzing.api.analysis;

import org.springframework.stereotype.Service;
import vn.ainalyzing.api.analysis.AnalysisResult.Warning;
import vn.ainalyzing.api.analysis.AnalyzeRequest.ManualDemand;
import vn.ainalyzing.api.analysis.ScenarioResult.OptionResult;
import vn.ainalyzing.api.analysis.ScenarioResult.Winner;
import vn.ainalyzing.api.csv.DemandForecaster;
import vn.ainalyzing.api.csv.SalesCsvParser;
import vn.ainalyzing.api.csv.SalesDay;
import vn.ainalyzing.api.csv.SalesSummaryBuilder;
import vn.ainalyzing.api.csv.SalesSummaryBuilder.SalesSummary;
import vn.ainalyzing.api.demo.DemoCaseRepository;
import vn.ainalyzing.api.demo.DemoCaseRepository.DemoCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One press of Tính (UC-02/03/04/05): builds the three demand series from the chosen source, runs the FEFO
 * comparison for each, then derives facts and warnings. Throws CsvException (422/413), UnknownDemoCaseException
 * (400) or DataAccessException (503); the web layer maps them.
 */
@Service
public class AnalysisService {

    private final DemoCaseRepository demoCases;

    public AnalysisService(DemoCaseRepository demoCases) {
        this.demoCases = demoCases;
    }

    private record Demand(Map<Scenario, int[]> series, DemandSourceInfo info, SalesSummary summary) {
    }

    /** @param csvText decoded CSV, required when the source is csv and ignored otherwise */
    public AnalysisResult analyze(AnalyzeRequest req, String csvText) {
        Demand demand = switch (req.demandSource()) {
            case CSV -> fromCsv(req, csvText);
            case MANUAL -> fromManual(req.manualDemand());
            case DEMO -> fromDemo(req.demoCaseCode());
        };

        Map<Scenario, ScenarioResult> results = new EnumMap<>(Scenario.class);
        for (Scenario s : Scenario.values()) {
            results.put(s, PurchaseCalculator.compare(req.purchaseInput(), demand.series().get(s)));
        }
        return new AnalysisResult(req, results, demand.info(), demand.summary(),
                FactsBuilder.build(req, results, demand.summary()), warnings(results));
    }

    private static Demand fromCsv(AnalyzeRequest req, String csvText) {
        List<SalesDay> days = SalesCsvParser.parse(csvText, req.receiveDate());
        DemandForecaster.Forecast f = DemandForecaster.forecast(days, req.receiveDate());
        Map<Scenario, int[]> series = new EnumMap<>(Scenario.class);
        series.put(Scenario.SLOW, f.slow());
        series.put(Scenario.BASE, f.base());
        series.put(Scenario.FAST, f.fast());
        DemandSourceInfo info = new DemandSourceInfo("csv", f.daysUsed(),
                Weekdays.byCode(f.inStockSamplesByWeekday(), n -> n), null, false);
        return new Demand(series, info, SalesSummaryBuilder.build(days, req.receiveDate()));
    }

    private static Demand fromManual(ManualDemand m) {
        Map<Scenario, int[]> series = new EnumMap<>(Scenario.class);
        series.put(Scenario.SLOW, flat(m.slow()));
        series.put(Scenario.BASE, flat(m.base()));
        series.put(Scenario.FAST, flat(m.fast()));
        return new Demand(series, new DemandSourceInfo("manual", null, null, null, false), null);
    }

    /** "Với mã ca demo, Spring Boot lấy dãy nhu cầu theo mã ca từ PostgreSQL và dùng giá, hạn đã chỉnh trong form." */
    private Demand fromDemo(String code) {
        DemoCase c = demoCases.find(code).orElseThrow(() -> new UnknownDemoCaseException(code));
        Map<Scenario, int[]> series = new EnumMap<>(Scenario.class);
        series.put(Scenario.SLOW, c.demandSlow());
        series.put(Scenario.BASE, c.demandBase());
        series.put(Scenario.FAST, c.demandFast());
        return new Demand(series, new DemandSourceInfo("demo", null, null, c.caseCode(), true), null);
    }

    private static int[] flat(int perDay) {
        int[] d = new int[PurchaseCalculator.HORIZON_DAYS];
        Arrays.fill(d, perDay);
        return d;
    }

    static List<Warning> warnings(Map<Scenario, ScenarioResult> results) {
        List<Warning> out = new ArrayList<>();
        for (Scenario s : Scenario.values()) {
            ScenarioResult r = results.get(s);
            if (negative(r.a())) {
                out.add(new Warning("NEGATIVE_MARGIN", s, "A"));
            }
            if (negative(r.b())) {
                out.add(new Warning("NEGATIVE_MARGIN", s, "B"));
            }
        }
        long distinct = results.values().stream().map(ScenarioResult::winner).distinct().count();
        boolean anyDecision = results.values().stream().anyMatch(r -> r.winner() != Winner.NONE);
        if (distinct > 1 && anyDecision) {
            out.add(new Warning("WINNER_DIFFERS_BY_SCENARIO", null, null));
        }
        return List.copyOf(out);
    }

    private static boolean negative(OptionResult o) {
        return o.marginVnd() != null && o.marginVnd() < 0;
    }
}
