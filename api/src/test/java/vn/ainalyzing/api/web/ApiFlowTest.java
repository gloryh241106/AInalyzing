package vn.ainalyzing.api.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.ainalyzing.api.AppConfig;
import vn.ainalyzing.api.Fixtures;
import vn.ainalyzing.api.analysis.AnalysisService;
import vn.ainalyzing.api.analysis.SnapshotStore;
import vn.ainalyzing.api.demo.DemoCaseRepository;
import vn.ainalyzing.api.llm.ChatService;
import vn.ainalyzing.api.llm.GroqClient;
import vn.ainalyzing.api.llm.GroqGate;
import vn.ainalyzing.api.llm.InsightService;
import vn.ainalyzing.api.llm.PromptBuilder;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract of docs/api.md with the database mocked and Groq disabled (template answers). */
@WebMvcTest(properties = {"groq.api-key=", "app.analysis.ttl=30m"})
@Import({AppConfig.class, AnalyzeRequestReader.class, AnalysisService.class, SnapshotStore.class, ChatService.class,
        InsightService.class, GroqClient.class, GroqGate.class, PromptBuilder.class})
class ApiFlowTest {

    static final String STD = AnalyzeRequestReaderTest.VALID;
    static final String CSV_REQ = STD.replace("\"demandSource\":\"demo\"", "\"demandSource\":\"csv\"");

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @MockitoBean
    DemoCaseRepository demoCases;
    @MockitoBean
    JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        DemoCaseRepository real = Fixtures.demoRepository();
        when(demoCases.find(anyString())).thenAnswer(inv -> real.find(inv.getArgument(0)));
    }

    JsonNode json(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    String analyze(MockHttpSession session) throws Exception {
        MvcResult r = mvc.perform(post("/api/analyze").session(session).contentType(MediaType.APPLICATION_JSON).content(STD))
                .andExpect(status().isOk()).andReturn();
        return json(r).path("analysisId").asString();
    }

    MockMultipartFile requestPart(String json) {
        return new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void analyzeJsonReturnsA2Shape() throws Exception {
        mvc.perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON).content(STD))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.defaultScenario").value("BASE"))
                .andExpect(jsonPath("$.scenarios.BASE.options.A.marginVnd").value(76000))
                .andExpect(jsonPath("$.scenarios.BASE.options.B.unitsExpired").value(16))
                .andExpect(jsonPath("$.scenarios.FAST.winner").value("B"))
                .andExpect(jsonPath("$.conditions.BASE.maxWinningTotalVnd").value(403999))
                .andExpect(jsonPath("$.demandSourceInfo.source").value("demo"))
                .andExpect(jsonPath("$.input.quoteB.units").value(48))
                .andExpect(jsonPath("$.salesSummary").isEmpty())
                .andExpect(jsonPath("$.facts[0].id").exists());
    }

    @Test
    void multipartCsvRun() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "yogurt_56d.csv", "text/csv",
                Fixtures.demoCsv("yogurt_56d.csv").getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/analyze").file(requestPart(CSV_REQ)).file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demandSourceInfo.daysUsed").value(56))
                .andExpect(jsonPath("$.demandSourceInfo.inStockSamplesByWeekday.SAT").value(3))
                .andExpect(jsonPath("$.salesSummary.byWeekday.SAT.stockoutDays").value(5))
                .andExpect(jsonPath("$.scenarios.BASE.demand[5]").value(3));
    }

    @Test
    void csvErrors() throws Exception {
        mvc.perform(multipart("/api/analyze").file(requestPart(CSV_REQ)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"));

        String bad = "date,units_sold,stock_status\n2026-11-01,5,in_stock\n2026-11-01,5,in_stock\n";
        mvc.perform(multipart("/api/analyze").file(requestPart(CSV_REQ))
                        .file(new MockMultipartFile("file", "x.csv", "text/csv", bad.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].code").value("CSV_INVALID"))
                .andExpect(jsonPath("$.errors[0].row").value(3));

        byte[] latin1 = "date,units_sold,stock_status\n2026-11-01,5,in_stocké\n".getBytes(StandardCharsets.ISO_8859_1);
        mvc.perform(multipart("/api/analyze").file(requestPart(CSV_REQ))
                        .file(new MockMultipartFile("file", "x.csv", "text/csv", latin1)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].message").value("Tệp phải mã hóa UTF-8"));

        byte[] big = new byte[1024 * 1024 + 1];
        mvc.perform(multipart("/api/analyze").file(requestPart(CSV_REQ))
                        .file(new MockMultipartFile("file", "x.csv", "text/csv", big)))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.errors[0].code").value("FILE_TOO_LARGE"));

        StringBuilder rows = new StringBuilder("date,units_sold,stock_status\n");
        for (int i = 1; i <= 367; i++) {
            rows.append(Fixtures.D.minusDays(i)).append(",5,in_stock\n");
        }
        mvc.perform(multipart("/api/analyze").file(requestPart(CSV_REQ))
                        .file(new MockMultipartFile("file", "x.csv", "text/csv", rows.toString().getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isContentTooLarge());
    }

    @Test
    void validationAndDemoErrors() throws Exception {
        mvc.perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON).content(STD.replace("\"units\":48", "\"units\":24")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("quoteB.units"));

        mvc.perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON).content(STD.replace("YOGURT_STD", "NOPE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("demoCaseCode"));

        when(demoCases.find(anyString())).thenThrow(new CannotGetJdbcConnectionException("down"));
        mvc.perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON).content(STD))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errors[0].code").value("DB_UNAVAILABLE"));

        mvc.perform(post("/api/analyze").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void chatAndInsightAreScopedToTheSession() throws Exception {
        MockHttpSession mine = new MockHttpSession();
        String id = analyze(mine);

        mvc.perform(post("/api/analysis/chat").session(mine).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analysisId\":\"" + id + "\",\"question\":\"Vì sao chọn A?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("fallback"))
                .andExpect(jsonPath("$.fallbackReason").value("DISABLED"))
                .andExpect(jsonPath("$.answer.intent").value("WHY_OPTION"))
                .andExpect(jsonPath("$.answer.factIds[0]").value("F_A_MARGIN_BASE"));

        MvcResult card = mvc.perform(post("/api/analysis/insight").session(mine).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analysisId\":\"" + id + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("fallback"))
                .andExpect(jsonPath("$.recommendation.option").value("A"))
                .andReturn();
        assertTrue(json(card).path("insights").size() >= 2);

        MockHttpSession other = new MockHttpSession();
        mvc.perform(post("/api/analysis/chat").session(other).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analysisId\":\"" + id + "\",\"question\":\"Vì sao chọn A?\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("stale_analysis"));
        mvc.perform(post("/api/analysis/insight").contentType(MediaType.APPLICATION_JSON).content("{\"analysisId\":\"" + id + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void chatValidation() throws Exception {
        mvc.perform(post("/api/analysis/chat").contentType(MediaType.APPLICATION_JSON).content("{\"scenario\":\"MEDIUM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void resetMakesOldIdsStale() throws Exception {
        MockHttpSession s = new MockHttpSession();
        String id = analyze(s);
        mvc.perform(post("/api/session/reset").session(s)).andExpect(status().isNoContent());
        assertTrue(s.isInvalid());
        mvc.perform(post("/api/analysis/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analysisId\":\"" + id + "\",\"question\":\"Vì sao chọn A?\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/session/reset")).andExpect(status().isNoContent());
    }

    @Test
    void unknownRouteIsJson404() throws Exception {
        MvcResult r = mvc.perform(post("/api/nope")).andReturn();
        assertEquals(404, r.getResponse().getStatus());
        assertEquals("NOT_FOUND", json(r).path("errors").path(0).path("code").asString());
    }
}
