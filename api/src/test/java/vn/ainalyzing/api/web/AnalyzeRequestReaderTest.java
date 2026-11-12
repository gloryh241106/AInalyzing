package vn.ainalyzing.api.web;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import vn.ainalyzing.api.analysis.AnalyzeRequest;
import vn.ainalyzing.api.analysis.AnalyzeRequest.DemandSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** FR-02 / AC-01: wrong fields are named one by one; the pending shelf life stays valid. */
class AnalyzeRequestReaderTest {

    static final String VALID = """
            {"sku":"YOGURT-100G","receiveDate":"2026-11-13","sellPriceVnd":15000,"budgetVnd":432000,
             "openingStock":{"units":0,"sellableDays":null},
             "quoteA":{"units":24,"goodsVnd":284000,"deliveryVnd":0,"sellableDays":5},
             "quoteB":{"units":48,"goodsVnd":432000,"deliveryVnd":0,"sellableDays":5},
             "demandSource":"demo","manualDemand":null,"demoCaseCode":"YOGURT_STD"}""";

    final AnalyzeRequestReader reader = new AnalyzeRequestReader(JsonMapper.builder().build());

    List<String> fieldsOf(String json) {
        ApiException e = assertThrows(ApiException.class, () -> reader.read(json));
        assertEquals(400, e.status().value());
        e.errors().forEach(x -> assertEquals("VALIDATION_ERROR", x.code()));
        return e.errors().stream().map(ApiError::field).sorted().toList();
    }

    @Test
    void readsTheSpecExample() {
        AnalyzeRequest r = reader.read(VALID);
        assertEquals("YOGURT-100G", r.sku());
        assertEquals(48, r.quoteB().units());
        assertEquals(DemandSource.DEMO, r.demandSource());
        assertEquals("YOGURT_STD", r.demoCaseCode());
        assertNull(r.openingSellableDays());
    }

    @Test
    void ac01SmallerBAndFifteenDaysAreBothReported() {
        String json = VALID.replace("\"units\":48", "\"units\":24")
                .replace("\"goodsVnd\":432000,\"deliveryVnd\":0,\"sellableDays\":5", "\"goodsVnd\":432000,\"deliveryVnd\":0,\"sellableDays\":15");
        assertEquals(List.of("quoteB.sellableDays", "quoteB.units"), fieldsOf(json));
    }

    @Test
    void pendingShelfLifeIsAllowed() {
        AnalyzeRequest r = reader.read(VALID.replace("\"goodsVnd\":432000,\"deliveryVnd\":0,\"sellableDays\":5",
                "\"goodsVnd\":432000,\"deliveryVnd\":0,\"sellableDays\":null"));
        assertNull(r.quoteB().sellableDays());
    }

    @Test
    void everyBadTypeIsListed() {
        String json = """
                {"sku":"","receiveDate":"13/11/2026","sellPriceVnd":"15000","budgetVnd":-1,
                 "openingStock":{"units":3},
                 "quoteA":{"units":24.5,"goodsVnd":284000,"deliveryVnd":0,"sellableDays":5},
                 "quoteB":{"units":48,"goodsVnd":432000,"sellableDays":0},
                 "demandSource":"excel"}""";
        assertEquals(List.of("budgetVnd", "demandSource", "openingStock.sellableDays", "quoteA.units",
                "quoteB.deliveryVnd", "quoteB.sellableDays", "receiveDate", "sellPriceVnd", "sku"), fieldsOf(json));
    }

    @Test
    void manualDemandMustBeOrdered() {
        String json = VALID.replace("\"demandSource\":\"demo\",\"manualDemand\":null",
                "\"demandSource\":\"manual\",\"manualDemand\":{\"slow\":7,\"base\":6,\"fast\":5}");
        assertEquals(List.of("manualDemand.fast", "manualDemand.slow"), fieldsOf(json));
        AnalyzeRequest ok = reader.read(VALID.replace("\"demandSource\":\"demo\",\"manualDemand\":null",
                "\"demandSource\":\"manual\",\"manualDemand\":{\"slow\":5,\"base\":6,\"fast\":7}"));
        assertEquals(6, ok.manualDemand().base());
    }

    @Test
    void missingSourceSpecificFields() {
        assertEquals(List.of("manualDemand"), fieldsOf(VALID.replace("\"demandSource\":\"demo\"", "\"demandSource\":\"manual\"")));
        assertEquals(List.of("demoCaseCode"), fieldsOf(VALID.replace("\"demoCaseCode\":\"YOGURT_STD\"", "\"demoCaseCode\":null")));
    }

    @Test
    void notJsonIsOneError() {
        ApiException e = assertThrows(ApiException.class, () -> reader.read("not json"));
        assertEquals(1, e.errors().size());
        assertNull(e.errors().getFirst().field());
    }

    @Test
    void openingStockNeedsDays() {
        assertEquals(List.of("openingStock.sellableDays"),
                fieldsOf(VALID.replace("\"openingStock\":{\"units\":0,\"sellableDays\":null}", "\"openingStock\":{\"units\":10,\"sellableDays\":null}")));
        AnalyzeRequest r = reader.read(VALID.replace("\"openingStock\":{\"units\":0,\"sellableDays\":null}",
                "\"openingStock\":{\"units\":10,\"sellableDays\":2}"));
        assertEquals(10, r.openingUnits());
        assertEquals(2, r.openingSellableDays());
    }
}
