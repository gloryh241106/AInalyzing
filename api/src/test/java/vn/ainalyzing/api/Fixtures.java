package vn.ainalyzing.api;

import vn.ainalyzing.api.analysis.AnalysisResult;
import vn.ainalyzing.api.analysis.AnalysisService;
import vn.ainalyzing.api.analysis.AnalyzeRequest;
import vn.ainalyzing.api.analysis.AnalyzeRequest.DemandSource;
import vn.ainalyzing.api.analysis.Quote;
import vn.ainalyzing.api.demo.DemoCaseRepository;
import vn.ainalyzing.api.demo.DemoCaseRepository.DemoCase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The Specification's synthetic yogurt case (Phụ lục A.5): D = Friday 13/11/2026, A 24 jars / 284,000, B 48 jars. */
public final class Fixtures {

    public static final LocalDate D = LocalDate.of(2026, 11, 13);
    public static final int[] SLOW = twoWeeks(5, 5, 5, 6, 6, 2, 5);
    public static final int[] BASE = twoWeeks(6, 6, 6, 7, 7, 3, 6);
    public static final int[] FAST = twoWeeks(7, 7, 7, 8, 8, 4, 7);

    private Fixtures() {
    }

    public static int[] twoWeeks(int... week) {
        int[] d = new int[14];
        for (int i = 0; i < 14; i++) {
            d[i] = week[i % 7];
        }
        return d;
    }

    /** Repository mock that knows every demo code and returns the A.5 series. */
    public static DemoCaseRepository demoRepository() {
        DemoCaseRepository repo = mock(DemoCaseRepository.class);
        when(repo.find(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            return code.startsWith("YOGURT")
                    ? Optional.of(new DemoCase(code, "synthetic", "{}", SLOW, BASE, FAST))
                    : Optional.empty();
        });
        return repo;
    }

    public static AnalysisService service() {
        return new AnalysisService(demoRepository());
    }

    /** Demo-source request; bDays null means B's shelf life is not confirmed. */
    public static AnalyzeRequest demo(Integer bDays, long budget) {
        return new AnalyzeRequest("YOGURT-100G", D, 15_000, budget, 0, null,
                new Quote(24, 284_000, 0, 5), new Quote(48, 432_000, 0, bDays),
                DemandSource.DEMO, null, "YOGURT_STD");
    }

    public static AnalyzeRequest withB(AnalyzeRequest r, Quote b) {
        return new AnalyzeRequest(r.sku(), r.receiveDate(), r.sellPriceVnd(), r.budgetVnd(), r.openingUnits(),
                r.openingSellableDays(), r.quoteA(), b, r.demandSource(), r.manualDemand(), r.demoCaseCode());
    }

    public static AnalyzeRequest withA(AnalyzeRequest r, Quote a) {
        return new AnalyzeRequest(r.sku(), r.receiveDate(), r.sellPriceVnd(), r.budgetVnd(), r.openingUnits(),
                r.openingSellableDays(), a, r.quoteB(), r.demandSource(), r.manualDemand(), r.demoCaseCode());
    }

    public static AnalysisResult std() {
        return service().analyze(demo(5, 432_000), null);
    }

    public static AnalysisResult sixDays() {
        return service().analyze(demo(6, 432_000), null);
    }

    public static AnalysisResult budget400k() {
        return service().analyze(demo(6, 400_000), null);
    }

    public static AnalysisResult pending() {
        return service().analyze(demo(null, 432_000), null);
    }

    public static AnalysisResult csv() throws IOException {
        AnalyzeRequest r = demo(5, 432_000);
        AnalyzeRequest csvReq = new AnalyzeRequest(r.sku(), r.receiveDate(), r.sellPriceVnd(), r.budgetVnd(), 0, null,
                r.quoteA(), r.quoteB(), DemandSource.CSV, null, null);
        return service().analyze(csvReq, demoCsv("yogurt_56d.csv"));
    }

    public static String demoCsv(String name) throws IOException {
        return Files.readString(Path.of("..", "data", "demo", name));
    }
}
