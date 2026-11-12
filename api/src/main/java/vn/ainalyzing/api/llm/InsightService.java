package vn.ainalyzing.api.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.ainalyzing.api.analysis.AnalysisSnapshot;
import vn.ainalyzing.api.analysis.Scenario;
import vn.ainalyzing.api.llm.FactBook.Rendered;
import vn.ainalyzing.api.llm.Template.Group;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Spec A.3 insight card. Groq picks the scenario to look at first, the insights and the supplier questions as
 * template ids; Java validates them and writes the text. The card is computed once per analysisId (memo on the
 * snapshot); a second call returns the same content without calling Groq. When Groq cannot be used the card is
 * built by fixed rules (BR-12) with status fallback.
 */
@Service
public class InsightService {

    private static final Logger log = LoggerFactory.getLogger(InsightService.class);
    static final int MAX_INSIGHTS = 4;
    static final int MAX_QUESTIONS = 3;

    /** BR-12 rule order: what blocks a quote, then what changes the decision, then the sales-history signals. */
    private static final List<String> FALLBACK_INSIGHTS = List.of("INS_B_PENDING", "INS_B_OVER_BUDGET",
            "INS_WINNER_CHANGES", "INS_WINNER_STABLE", "INS_STOCKOUT_WEEKDAY", "INS_TREND", "INS_NEGATIVE_MARGIN",
            "INS_B_EXPIRY", "INS_ANOMALY", "INS_A_SHORTAGE");
    private static final List<String> FALLBACK_QUESTIONS = List.of("Q_CONFIRM_SHELF_LIFE", "Q_SHELF_LIFE", "Q_PRICE");

    private final GroqProperties props;
    private final GroqClient client;
    private final GroqGate gate;
    private final PromptBuilder prompts;
    private final ObjectMapper mapper;

    public InsightService(GroqProperties props, GroqClient client, GroqGate gate, PromptBuilder prompts, ObjectMapper mapper) {
        this.props = props;
        this.client = client;
        this.gate = gate;
        this.prompts = prompts;
        this.mapper = mapper;
    }

    public InsightResponse insight(AnalysisSnapshot snapshot) {
        return snapshot.memo("insight", () -> compute(snapshot));
    }

    private InsightResponse compute(AnalysisSnapshot snapshot) {
        FactBook book = new FactBook(snapshot.result());
        long start = System.nanoTime();
        FallbackReason reason;
        if (!props.enabled()) {
            reason = FallbackReason.DISABLED;
        } else if (!props.egressAllowed(book.source())) {
            reason = FallbackReason.LOCAL_POLICY;
        } else if (!gate.tryAcquire()) {
            reason = FallbackReason.RATE_LIMITED;
        } else {
            try {
                String content = client.complete(prompts.insight(book), Duration.ofMillis(props.timeoutMs()), "insight").content();
                InsightResponse card = plan(snapshot.id(), book, content);
                log(card.status(), null, start);
                return card;
            } catch (GroqException e) {
                reason = e.reason();
                if (reason == FallbackReason.RATE_LIMITED && e.retryAfter() != null) {
                    gate.pause(e.retryAfter());
                }
            }
        }
        log("fallback", reason, start);
        return fallback(snapshot.id(), book, reason);
    }

    InsightResponse plan(String analysisId, FactBook book, String content) {
        PlanReader reader = new PlanReader(mapper, book);
        JsonNode json = reader.json(content);

        Scenario suggested = reader.scenario(json, "suggestedScenario");
        JsonNode reasonNode = reader.object(json, "scenarioReason");
        Template reasonT = reader.template(reasonNode, Group.SCENARIO_REASON, suggested);
        reader.factIds(reasonNode);

        List<InsightResponse.Insight> insights = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode item : reader.array(json, "insights")) {
            Scenario s = reader.scenario(item);
            Template t = reader.template(item, Group.INSIGHT, s);
            reader.factIds(item);
            Scenario at = t.perScenario() ? s : Scenario.BASE;
            if (insights.size() < MAX_INSIGHTS && seen.add(t.id() + "@" + at)) {
                Rendered r = t.renderFor(book, at);
                insights.add(new InsightResponse.Insight(t.kind(), r.text(), r.factIds()));
            }
        }

        JsonNode recNode = reader.object(json, "recommendation");
        String option = reader.text(recNode, "option");
        String winner = book.r(suggested).winner().name();
        if (!winner.equals(option)) {
            throw PlanReader.invalid("recommendation differs from the Java winner");
        }
        Template recT = reader.template(recNode, Group.WHY_OPTION, suggested);
        reader.factIds(recNode);
        Rendered rec = recT.renderFor(book, suggested);

        List<InsightResponse.Item> questions = new ArrayList<>();
        Set<String> seenQ = new LinkedHashSet<>();
        for (JsonNode item : reader.array(json, "supplierQuestions")) {
            Scenario s = reader.scenario(item);
            Template t = reader.template(item, Group.SUPPLIER_QUESTION, s);
            reader.factIds(item);
            Scenario at = t.perScenario() ? s : Scenario.BASE;
            Rendered r = t.renderFor(book, at);
            if (questions.size() < MAX_QUESTIONS && seenQ.add(r.text())) {
                questions.add(new InsightResponse.Item(r.text(), r.factIds()));
            }
        }

        Rendered why = reasonT.renderFor(book, suggested);
        return new InsightResponse(analysisId, "ok", null, props.model(), PromptBuilder.PROMPT_VERSION, suggested,
                new InsightResponse.Item(why.text(), why.factIds()), insights,
                new InsightResponse.Recommendation(winner, rec.text(), rec.factIds()), questions);
    }

    /** BR-12: the same card shape from fixed rules, BASE scenario, no model involved. */
    static InsightResponse fallback(String analysisId, FactBook book, FallbackReason reason) {
        Scenario s = Scenario.BASE;
        Rendered why = TemplateCatalog.byId("SCENARIO_BASE_DEFAULT").orElseThrow().renderFor(book, s);

        List<InsightResponse.Insight> insights = new ArrayList<>();
        for (String id : FALLBACK_INSIGHTS) {
            Template t = TemplateCatalog.byId(id).orElseThrow();
            if (insights.size() < MAX_INSIGHTS && t.appliesTo(book, s)) {
                Rendered r = t.renderFor(book, s);
                insights.add(new InsightResponse.Insight(t.kind(), r.text(), r.factIds()));
            }
        }

        Rendered rec = TemplateCatalog.chatTemplate(Group.WHY_OPTION, book, s).renderFor(book, s);

        List<InsightResponse.Item> questions = new ArrayList<>();
        for (String id : FALLBACK_QUESTIONS) {
            Template t = TemplateCatalog.byId(id).orElseThrow();
            if (questions.size() < 2 && t.appliesTo(book, s)) {
                Rendered r = t.renderFor(book, s);
                questions.add(new InsightResponse.Item(r.text(), r.factIds()));
            }
        }
        return new InsightResponse(analysisId, "fallback", reason, null, PromptBuilder.PROMPT_VERSION, s,
                new InsightResponse.Item(why.text(), why.factIds()), insights,
                new InsightResponse.Recommendation(book.r(s).winner().name(), rec.text(), rec.factIds()), questions);
    }

    private void log(String status, FallbackReason reason, long start) {
        log.info("insight status={} reason={} ms={} model={} prompt={}", status, reason,
                (System.nanoTime() - start) / 1_000_000, props.model(), PromptBuilder.PROMPT_VERSION);
    }
}
